var Clay = require('@rebble/clay');
var clay = new Clay(require('./config'));
var phoneBattery = null;

function sendWeather(lat, lon) {
  var url = 'https://api.open-meteo.com/v1/forecast?latitude=' + lat + '&longitude=' + lon + '&current=temperature_2m&temperature_unit=celsius';
  var xhr = new XMLHttpRequest();
  xhr.onload = function() {
    if (xhr.status === 200) {
      try {
        var data = JSON.parse(xhr.responseText);
        Pebble.sendAppMessage({Temperature: Math.round(data.current.temperature_2m)});
      } catch (e) {
        console.log('weather parse: ' + e);
      }
    }
  };
  xhr.open('GET', url, true);
  xhr.send();
}

function geocodeAndWeather(name) {
  var xhr = new XMLHttpRequest();
  xhr.onload = function() {
    if (xhr.status === 200) {
      try {
        var d = JSON.parse(xhr.responseText);
        if (d.results && d.results.length) {
          sendWeather(d.results[0].latitude, d.results[0].longitude);
        }
      } catch (e) {
        console.log('geocode parse: ' + e);
      }
    }
  };
  xhr.open('GET', 'https://geocoding-api.open-meteo.com/v1/search?count=1&language=ru&format=json&name=' + encodeURIComponent(name), true);
  xhr.send();
}

function updateWeather() {
  var settings = JSON.parse(localStorage.getItem('clay-settings') || '{}');
  var autoLoc = settings.AutoLocation;
  if (autoLoc === undefined || autoLoc === true || autoLoc === 1 || autoLoc === '1') {
    navigator.geolocation.getCurrentPosition(function(pos) {
      sendWeather(pos.coords.latitude, pos.coords.longitude);
    }, function(err) {
      console.log('geo: ' + err.message);
    }, {enableHighAccuracy:false, maximumAge:1800000, timeout:10000});
  } else if (settings.ManualLocation) {
    geocodeAndWeather(settings.ManualLocation);
  }
}

function sendPhoneBatteryValue(level) {
  var pct = Math.max(0, Math.min(100, Math.round(level * 100)));
  Pebble.sendAppMessage({PhoneBattery: pct});
}

function updatePhoneBattery() {
  try {
    if (phoneBattery && typeof phoneBattery.level === 'number') {
      sendPhoneBatteryValue(phoneBattery.level);
      return;
    }
    if (navigator && typeof navigator.getBattery === 'function') {
      navigator.getBattery().then(function(battery) {
        phoneBattery = battery;
        sendPhoneBatteryValue(battery.level);
        if (battery.addEventListener) {
          battery.addEventListener('levelchange', function() {
            sendPhoneBatteryValue(battery.level);
          });
        }
      }).catch(function(err) {
        console.log('phone battery unavailable: ' + err);
      });
    } else {
      console.log('phone battery API unavailable in PebbleKit JS');
    }
  } catch (e) {
    console.log('phone battery error: ' + e);
  }
}

Pebble.addEventListener('ready', function() {
  updateWeather();
  updatePhoneBattery();
});

Pebble.addEventListener('appmessage', function(e) {
  if (e.payload.RequestWeather) {
    updateWeather();
    updatePhoneBattery();
  }
});

Pebble.addEventListener('webviewclosed', function() {
  setTimeout(function() {
    updateWeather();
    updatePhoneBattery();
  }, 800);
});
