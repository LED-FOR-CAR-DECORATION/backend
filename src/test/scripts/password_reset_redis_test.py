"""Execute the production Lua script against an isolated Redis emulator with Lua support.

Install test-only dependencies: pip install 'fakeredis[lua]'
Run: python src/test/scripts/password_reset_redis_test.py
"""
import concurrent.futures
from pathlib import Path
import re
import unittest
from unittest.mock import patch
import fakeredis

SOURCE = Path(__file__).resolve().parents[2] / 'main/java/com/lumintra/backend/iam/infrastructure/security/RedisPasswordResetOtpStore.java'
_text = SOURCE.read_text(encoding='utf-8')
ISSUE_SCRIPT = re.search(r'ISSUE\s*=\s*new DefaultRedisScript<>\("""(.*?)""", Long.class\)', _text, re.S)[1]
VALIDATE_SCRIPT = re.search(r'VALIDATE_AND_CONSUME\s*=\s*new DefaultRedisScript<>\("""(.*?)""", Long.class\)', _text, re.S)[1]

class PasswordResetRedisScriptTest(unittest.TestCase):
    def setUp(self):
        self.redis = fakeredis.FakeStrictRedis()
        self.keys = ['auth:password-reset:{test}:otp', 'auth:password-reset:{test}:requests', 'auth:password-reset:{test}:lock']
        self.val_keys = ['auth:password-reset:{test}:otp', 'auth:password-reset:{test}:verify_attempts']

    def issue(self, value='protected-digest'):
        return self.redis.eval(ISSUE_SCRIPT, 3, *self.keys, value)

    def validate(self, value='protected-digest'):
        return self.redis.eval(VALIDATE_SCRIPT, 2, *self.val_keys, value)

    def test_first_three_requests_allowed_fourth_locks(self):
        self.assertEqual([self.issue() for _ in range(4)], [1, 1, 1, 0])
        self.assertEqual(self.redis.ttl(self.keys[2]), 86400)

    def test_otp_has_five_minute_ttl(self):
        self.issue()
        self.assertEqual(self.redis.ttl(self.keys[0]), 300)

    def test_new_request_replaces_challenge(self):
        self.issue('first'); self.issue('second')
        self.assertEqual(self.redis.get(self.keys[0]), b'second')

    def test_lock_rejects_without_overwriting_otp(self):
        for _ in range(4): self.issue('original')
        self.assertEqual(self.issue('replacement'), 0)
        self.assertEqual(self.redis.get(self.keys[0]), b'original')

    def test_unknown_email_consumes_quota_without_creating_otp(self):
        self.assertEqual([self.issue('') for _ in range(4)], [1, 1, 1, 0])
        self.assertFalse(self.redis.exists(self.keys[0]))

    def test_counter_window_does_not_slide(self):
        self.issue(); self.redis.expire(self.keys[1], 100)
        self.issue()
        self.assertLessEqual(self.redis.ttl(self.keys[1]), 100)

    def test_rejected_requests_do_not_extend_lock(self):
        for _ in range(4): self.issue()
        self.redis.expire(self.keys[2], 100)
        self.assertEqual(self.issue(), 0)
        self.assertLessEqual(self.redis.ttl(self.keys[2]), 100)

    def test_lock_expiry_allows_a_new_window(self):
        import time
        now = time.time()
        with patch('time.time', return_value=now):
            for _ in range(4): self.issue()
        with patch('time.time', return_value=now + 86401):
            self.assertEqual(self.issue(), 1)
            self.assertEqual(self.redis.get(self.keys[1]), b'1')

    def test_parallel_requests_allow_exactly_three(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
            results = list(pool.map(lambda _: self.issue(), range(24)))
        self.assertEqual(results.count(1), 3)
        self.assertEqual(results.count(0), 21)

    def test_validate_correct_otp_returns_1_and_consumes_key(self):
        self.issue('digest-123')
        self.assertEqual(self.validate('digest-123'), 1)
        self.assertFalse(self.redis.exists(self.val_keys[0]))
        self.assertFalse(self.redis.exists(self.val_keys[1]))

    def test_validate_wrong_otp_returns_0_and_tracks_attempts(self):
        self.issue('digest-123')
        self.assertEqual(self.validate('wrong-digest'), 0)
        self.assertEqual(self.redis.get(self.val_keys[1]), b'1')
        self.assertEqual(self.redis.ttl(self.val_keys[1]), 300)

    def test_validate_fifth_wrong_otp_returns_minus_2_and_invalidates_key(self):
        self.issue('digest-123')
        results = [self.validate('wrong-digest') for _ in range(5)]
        self.assertEqual(results, [0, 0, 0, 0, -2])
        self.assertFalse(self.redis.exists(self.val_keys[0]))

    def test_validate_missing_or_expired_otp_returns_minus_1(self):
        self.assertEqual(self.validate('digest-123'), -1)

    def test_validate_consumed_otp_cannot_be_reused(self):
        self.issue('digest-single-use')
        self.assertEqual(self.validate('digest-single-use'), 1)
        self.assertEqual(self.validate('digest-single-use'), -1)

if __name__ == '__main__':
    unittest.main(verbosity=2)
