from pathlib import Path

path = Path("app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java")
text = path.read_text()
old_resume = '''    @Override
    protected void onResume() {
        super.onResume();
        if (!rearMode) {
            refreshSystemAccessStatus();
        }
    }
'''
new_resume = '''    @Override
    protected void onResume() {
        super.onResume();
        if (!rearMode) {
            refreshSystemAccessStatus();
            if (hasNotificationAccess()) {
                PebbleRuntimeService.start(this);
            }
        }
    }
'''
if text.count(old_resume) != 1:
    raise SystemExit("onResume block not found")
text = text.replace(old_resume, new_resume, 1)
old_result = '''        if (requestCode != REQUEST_POST_NOTIFICATIONS) {
            return;
        }
        if (!isIgnoringBatteryOptimizations()) {
'''
new_result = '''        if (requestCode != REQUEST_POST_NOTIFICATIONS) {
            return;
        }
        PebbleRuntimeService.start(this);
        if (!isIgnoringBatteryOptimizations()) {
'''
if text.count(old_result) != 1:
    raise SystemExit("notification result block not found")
text = text.replace(old_result, new_result, 1)
path.write_text(text)
print("notification repost safeguard applied")
