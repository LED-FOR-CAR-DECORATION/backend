# Temporary schema initialization exception

The repository normally requires versioned database migrations. For this undeployed IAM implementation, the reviewer explicitly requested removing Flyway and using Hibernate `ddl-auto=create` until all entities have been imported from the approved database model: [PR #11 review](https://github.com/LED-FOR-CAR-DECORATION/backend/pull/11#issuecomment-6463797379). The user authorized implementing the review feedback in this task.

Scope: the development profile only. Production retains `ddl-auto=validate`. Automated tests use dedicated H2 databases with `create-drop`; they do not run the application against the development database. This task does not launch the development profile or modify real data.

Flyway dependencies, settings and SQL migrations are removed from the feature stack. Restore an agreed migration strategy before deployment; do not treat this development exception as approval to recreate a production database.

The current Drive ERD defines `REFRESH_TOKEN` with `account_id` directly, so token persistence uses that table instead of adding `auth_sessions` or refresh-token columns to `user_account`. See [schema-reference.md](schema-reference.md).
