from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one UI match in {path}, found {count}: {old[:100]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


application = "app/src/main/java/com/manufacttest/pebblereardisplay/PebblehertzApplication.java"
replace_once(
    application,
    "import android.widget.Toast;\n",
    "import android.widget.Toast;\n\n"
    "import com.manufacttest.pebblereardisplay.ui.RuntimeModeUi;\n",
)
replace_once(
    application,
    "        super.onCreate();\n        registerActivityLifecycleCallbacks(this);\n",
    "        super.onCreate();\n"
    "        RuntimeModeUi.initializeMigration(this);\n"
    "        registerActivityLifecycleCallbacks(this);\n",
)
replace_once(
    application,
    "        View decor = activity.getWindow().getDecorView();\n"
    "        bindSupportButton(activity, decor);\n",
    "        View decor = activity.getWindow().getDecorView();\n"
    "        bindSupportButton(activity, decor);\n"
    "        RuntimeModeUi.bind(activity, decor);\n"
    "        RuntimeModeUi.scheduleMigrationIntro(activity, decor);\n",
)
replace_once(
    application,
    "        ViewTreeObserver.OnGlobalLayoutListener listener =\n"
    "                () -> bindSupportButton(activity, decor);\n",
    "        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {\n"
    "            bindSupportButton(activity, decor);\n"
    "            RuntimeModeUi.bind(activity, decor);\n"
    "        };\n",
)

activity = "app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java"
replace_once(
    activity,
    "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU\n"
    "                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)\n"
    "                != PackageManager.PERMISSION_GRANTED) {\n",
    "        if (preferences != null\n"
    "                && preferences.isReliableRuntime()\n"
    "                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU\n"
    "                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)\n"
    "                != PackageManager.PERMISSION_GRANTED) {\n",
)
