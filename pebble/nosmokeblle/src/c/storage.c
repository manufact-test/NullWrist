#include "storage.h"

enum {
  KEY_LAST_SMOKED=1, KEY_CIGS=2, KEY_MONEY=3, KEY_ATTEMPTS=4,
  KEY_BEST=5, KEY_TOTAL_CIGS=6, KEY_TOTAL_MONEY=7, KEY_CONFIGURED=8,
  KEY_NOTIFIED_MASK=9
};

void storage_load(AppState *s) {
  s->last_smoked = persist_exists(KEY_LAST_SMOKED) ? persist_read_int(KEY_LAST_SMOKED) : time(NULL);
  s->cigarettes_per_day = persist_exists(KEY_CIGS) ? persist_read_int(KEY_CIGS) : 20;
  s->money_cents_per_day = persist_exists(KEY_MONEY) ? persist_read_int(KEY_MONEY) : 300;
  s->attempts = persist_exists(KEY_ATTEMPTS) ? persist_read_int(KEY_ATTEMPTS) : 0;
  s->best_streak = persist_exists(KEY_BEST) ? persist_read_int(KEY_BEST) : 0;
  s->lifetime_avoided_milli = persist_exists(KEY_TOTAL_CIGS) ? persist_read_int(KEY_TOTAL_CIGS) : 0;
  s->lifetime_saved_cents_milli = persist_exists(KEY_TOTAL_MONEY) ? persist_read_int(KEY_TOTAL_MONEY) : 0;
  s->notified_mask = persist_exists(KEY_NOTIFIED_MASK) ? (uint32_t)persist_read_int(KEY_NOTIFIED_MASK) : 0;
  s->configured = persist_exists(KEY_CONFIGURED) && persist_read_bool(KEY_CONFIGURED);
}

void storage_save(const AppState *s) {
  persist_write_int(KEY_LAST_SMOKED, (int)s->last_smoked);
  persist_write_int(KEY_CIGS, s->cigarettes_per_day);
  persist_write_int(KEY_MONEY, s->money_cents_per_day);
  persist_write_int(KEY_ATTEMPTS, s->attempts);
  persist_write_int(KEY_BEST, (int)s->best_streak);
  persist_write_int(KEY_TOTAL_CIGS, s->lifetime_avoided_milli);
  persist_write_int(KEY_TOTAL_MONEY, s->lifetime_saved_cents_milli);
  persist_write_int(KEY_NOTIFIED_MASK, (int)s->notified_mask);
  persist_write_bool(KEY_CONFIGURED, s->configured);
}

void storage_record_relapse(AppState *s, time_t now) {
  time_t elapsed = now > s->last_smoked ? now - s->last_smoked : 0;
  if (elapsed > s->best_streak) s->best_streak = elapsed;
  s->lifetime_avoided_milli += (int32_t)(((int64_t)elapsed * s->cigarettes_per_day * 1000) / 86400);
  s->lifetime_saved_cents_milli += (int32_t)(((int64_t)elapsed * s->money_cents_per_day * 1000) / 86400);
  s->attempts++;
  s->last_smoked = now;
  s->notified_mask = 0;
  storage_save(s);
}
