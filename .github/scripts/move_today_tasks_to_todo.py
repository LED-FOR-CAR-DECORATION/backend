#!/usr/bin/env python3
"""
Move tasks whose start date is today (or earlier) to "Todo" status in GitHub ProjectV2.
"""

import os
import sys
import json
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta

PROJECT_ID = os.environ.get("PROJECT_ID", "PVT_kwDOEzauPs4Bn1lX")
STATUS_FIELD_ID = os.environ.get("STATUS_FIELD_ID", "PVTSSF_lADOEzauPs4Bn1lXzhmYX3Y")
STATUS_TODO_ID = os.environ.get("STATUS_TODO_ID", "e653486d")  # "Todo" option ID
BACKLOG_NAME = os.environ.get("BACKLOG_NAME", "Backlog")
TIMEZONE_HOURS = int(os.environ.get("TIMEZONE_OFFSET_HOURS", "7"))  # Default UTC+7 (Vietnam)
DRY_RUN = os.environ.get("DRY_RUN", "false").lower() in ("true", "1", "yes")

TOKEN = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
if not TOKEN:
    # Attempt local fallback via gh cli
    try:
        import subprocess
        TOKEN = subprocess.check_output(["gh", "auth", "token"], text=True).strip()
    except Exception:
        print("ERROR: Neither GH_TOKEN nor GITHUB_TOKEN environment variable is set.")
        sys.exit(1)


def run_graphql(query: str, variables: dict = None) -> dict:
    payload = {"query": query}
    if variables:
        payload["variables"] = variables
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        "https://api.github.com/graphql",
        data=data,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Content-Type": "application/json",
            "User-Agent": "GitHubActions-Sprint-Automation",
        },
    )
    try:
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            if "errors" in body:
                print("GraphQL Errors:", json.dumps(body["errors"], indent=2), file=sys.stderr)
            return body
    except urllib.error.HTTPError as e:
        error_content = e.read().decode("utf-8")
        print(f"HTTP Error {e.code}: {e.reason}\n{error_content}", file=sys.stderr)
        raise


QUERY_ITEMS = """
query($projectId: ID!, $cursor: String) {
  node(id: $projectId) {
    ... on ProjectV2 {
      items(first: 100, after: $cursor) {
        pageInfo {
          hasNextPage
          endCursor
        }
        nodes {
          id
          status: fieldValueByName(name: "Status") {
            ... on ProjectV2ItemFieldSingleSelectValue {
              name
              optionId
            }
          }
          startDate: fieldValueByName(name: "Start date") {
            ... on ProjectV2ItemFieldDateValue {
              date
            }
            ... on ProjectV2ItemIssueFieldValue {
              issueFieldValue {
                ... on IssueFieldDateValue {
                  value
                }
              }
            }
          }
          content {
            ... on Issue {
              number
              title
              url
            }
            ... on DraftIssue {
              title
            }
          }
        }
      }
    }
  }
}
"""

MUTATION_UPDATE_STATUS = """
mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $optionId: String!) {
  updateProjectV2ItemFieldValue(input: {
    projectId: $projectId,
    itemId: $itemId,
    fieldId: $fieldId,
    value: {
      singleSelectOptionId: $optionId
    }
  }) {
    projectV2Item {
      id
    }
  }
}
"""


def fetch_all_items(project_id: str):
    all_items = []
    cursor = None
    while True:
        res = run_graphql(QUERY_ITEMS, {"projectId": project_id, "cursor": cursor})
        if not res or "data" not in res or not res["data"].get("node"):
            break
        items_conn = res["data"]["node"]["items"]
        all_items.extend(items_conn["nodes"])
        if items_conn["pageInfo"]["hasNextPage"]:
            cursor = items_conn["pageInfo"]["endCursor"]
        else:
            break
    return all_items


def extract_start_date(item: dict) -> str:
    s_date_obj = item.get("startDate")
    if not s_date_obj:
        return None
    if "date" in s_date_obj and s_date_obj["date"]:
        return s_date_obj["date"]
    if "issueFieldValue" in s_date_obj and s_date_obj["issueFieldValue"]:
        return s_date_obj["issueFieldValue"].get("value")
    return None


def main():
    tz = timezone(timedelta(hours=TIMEZONE_HOURS))
    today_str = datetime.now(tz).strftime("%Y-%m-%d")

    print(f"=== Daily Sprint Automation ===")
    print(f"Target Project ID: {PROJECT_ID}")
    print(f"Current Date (UTC+{TIMEZONE_HOURS}): {today_str}")
    print(f"Dry Run Mode: {DRY_RUN}\n")

    print("Fetching project items...")
    items = fetch_all_items(PROJECT_ID)
    print(f"Total items in project: {len(items)}\n")

    tasks_to_move = []
    for it in items:
        status_name = it.get("status", {}).get("name") if it.get("status") else None
        s_date = extract_start_date(it)
        content = it.get("content") or {}
        title = content.get("title", "Untitled")
        number = content.get("number")

        # Move tasks that are in Backlog whose start date has arrived (<= today)
        if status_name == BACKLOG_NAME and s_date and s_date <= today_str:
            tasks_to_move.append({
                "id": it["id"],
                "number": number,
                "title": title,
                "start_date": s_date,
            })

    if not tasks_to_move:
        print("No tasks in Backlog have start date on or before today.")
        print("Everything is up to date!")
        return

    print(f"Found {len(tasks_to_move)} task(s) to move from '{BACKLOG_NAME}' to 'Todo':\n")
    moved_count = 0
    for t in tasks_to_move:
        prefix = f"#{t['number']}" if t["number"] else "[Draft]"
        print(f"-> {prefix} {t['title']} (Start: {t['start_date']})")
        if not DRY_RUN:
            res = run_graphql(
                MUTATION_UPDATE_STATUS,
                {
                    "projectId": PROJECT_ID,
                    "itemId": t["id"],
                    "fieldId": STATUS_FIELD_ID,
                    "optionId": STATUS_TODO_ID,
                },
            )
            if res and "data" in res and res["data"].get("updateProjectV2ItemFieldValue"):
                print("   Status updated to Todo.")
                moved_count += 1
            else:
                print("   Failed to update status.")
        else:
            print("   [DRY RUN] Would update status to Todo.")
            moved_count += 1

    action_word = "Would move" if DRY_RUN else "Successfully moved"
    print(f"\n{action_word} {moved_count}/{len(tasks_to_move)} tasks to 'Todo'.")


if __name__ == "__main__":
    main()
