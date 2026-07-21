# Bundled third-party watchfaces

The repository owner selected the following Pebble watchfaces from publicly accessible Pebble Appstore listings for inclusion in the application. The build pins each package by SHA-256 so a changed or corrupted upstream file cannot silently enter the APK.

Copyright and trademarks remain with their respective authors and rightsholders. The app preserves the original PBW package metadata and records the source listing for each bundled file.

| Bundled file | Face | Author field | Version | Platforms | Source and integrity |
|---|---|---|---:|---|---|
| `big-shadow-2.00.5.pbw` | Big Shadow | holcik@gmail.com | 2.00.5 | aplite, basalt, chalk, diorite, emery | [Appstore listing](https://apps.repebble.com/big-shadow_54fc76072bf1bfe03500002a); SHA-256 `55e8930ea87be349c1b78f56a5f1e634ad837f405374db488a762795093cfbaf` |
| `nyan-cat-8.9.pbw` | Nyan Cat | dezign999 | 8.9 | aplite, basalt, chalk, diorite | [Appstore listing](https://apps.repebble.com/nyan-cat_54f972b93dd477b391000017); SHA-256 `fc0d9717c7691b7d389c930af569cf5e0481ea8cac9c80219e0823992d248588` |
| `pip-boy-100-5.4.pbw` | Pip Boy 100 | Bert de Ruiter | 5.4 | aplite, basalt | [Appstore listing](https://apps.repebble.com/pip-boy-100_529efe551a66383057000051); SHA-256 `2421ac2ae2554cab2e473be199f8b727c67474fb7618709d6dd3cadcaaf55e63` |
| `modern-3.1.1.pbw` | Modern Watchface | Zalew | 3.1.1 | package does not declare `targetPlatforms` | [Appstore listing](https://apps.repebble.com/modern_52bb213af9846878c200015b); SHA-256 `ed128c7af8b6710adf0acbfdc7da4cb9b85a98d64dc26cf57ae289e86df5ff5c` |
| `mario-time-3.41.pbw` | Mario Time | Cluster | 3.41 | aplite, basalt, chalk, diorite | [Appstore listing](https://apps.repebble.com/mario-time-watchface_55431083b7d4a71c0000003b); SHA-256 `d3855f1a6eaa32f3d72a8e0580438699b737b42d2ffab17b81922b77827a179d` |
| `91-dub-4.21.pbw` | 91 Dub 4.0 | Orviwan | 4.21 | aplite, basalt, chalk, diorite | [Appstore listing](https://apps.repebble.com/91-dub-v4-0_52b231c2b70e1c159500009b); SHA-256 `70704f3e53265ebda5a78a4c7f9f98ee1d50521a9dde19731da7954c876038d4` |
| `yweather-3.7.pbw` | YWeather | David Rodríguez Rincón | 3.7 | aplite, basalt, chalk | [Appstore listing](https://apps.repebble.com/yweather_52cc44e045ffdd31dd000180); SHA-256 `606dad45560f0371e503ce963fc61daba395c2d937020836840d7a8e5e5ca7ad` |

The machine-readable source manifest is [`bundled-watchfaces.json`](bundled-watchfaces.json). The fetch script validates archive structure, confirms that each package identifies itself as a watchface, enforces a 20 MB limit and verifies the pinned checksum before moving a file into Android assets.
