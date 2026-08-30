function send(values) {
  Pebble.sendAppMessage(values, function () {}, function () {});
}

function sendPhoneBattery() {
  if (navigator.getBattery) {
    navigator.getBattery().then(function (battery) {
      send({1: Math.round(battery.level * 100)});
    });
  }
}

Pebble.addEventListener('ready', function () {
  sendPhoneBattery();
});

Pebble.addEventListener('appmessage', function () {
  sendPhoneBattery();
});
