var Clay = require('@rebble/clay');
var clay = new Clay(require('./config'));

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

Pebble.addEventListener('ready', updateWeather);
Pebble.addEventListener('appmessage', function(e) {
  if (e.payload.RequestWeather) updateWeather();
});
Pebble.addEventListener('webviewclosed', function() {
  setTimeout(updateWeather, 800);
});
