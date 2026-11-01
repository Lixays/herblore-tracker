// App settings for Capacitor.
//
// The build reads this file when it runs "cap sync". Besides the settings below, it also applies the
// Android extras (widgets, background notifications, icons, offline images, version number) by running
// android-extra/apply.py - so they are included even if the GitHub build script (build.yml) is old.
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

try {
  const manifest = path.join(__dirname, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');
  const script = path.join(__dirname, 'android-extra', 'apply.py');
  const done = path.join(__dirname, 'android', '.herb-applied');

  // Only once the Android project exists, and only once per build
  if (fs.existsSync(manifest) && fs.existsSync(script) && !fs.existsSync(done)) {
    console.log('--- Applying Android extras (android-extra/apply.py) ---');
    let result = spawnSync('python3', [script], { cwd: __dirname, stdio: 'inherit' });
    if (result.error) result = spawnSync('python', [script], { cwd: __dirname, stdio: 'inherit' });
    if (result.error || result.status !== 0) {
      console.error('android-extra/apply.py failed - see the messages above.');
      process.exit(1);
    }
  }
} catch (error) {
  console.error('Could not run the Android extras step:', error.message);
  process.exit(1);
}

module.exports = {
  appId: 'com.example.herbloretracker',
  appName: 'Herb Timer',
  webDir: 'www',
  plugins: {
    LocalNotifications: {
      smallIcon: 'ic_stat_herb'
    }
  }
};
