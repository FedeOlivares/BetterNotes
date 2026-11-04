# Device facts

## Xiaomi Pad 7 (Xiaomi spec page, checked 2026-11-03)
- 11.2 inch, 3200 x 2136, 345 ppi, up to 144 Hz.
- Touch sampling up to 360 Hz, 240 Hz with pen.
- The spec page lists HyperOS 2 and doesn't state the Android version. See below for what the owner's tablet runs.

## Owner's tablet (read over adb, 2026-11-04)
- Xiaomi Pad 7, model 2410CRP4CG (`uke`), arm64-v8a.
- Android 16 (API 36), HyperOS `OS3.0.304.0.WOZMIXM`.
- Display 2136 x 3200, density bucket 440. It reports xdpi 332.441 and ydpi 340.511.
- It lists refresh rates of 30, 48, 50, 60, 90, 120 and 144 Hz. That list doesn't show what the app actually gets; measure it.
- The reported dpi average about 336.5, some 2.5% below the spec's 345 ppi. `CanvasView` uses that average, so real size may come out about 2.5% small. Nobody has measured this on the glass. A calibration setting would fix it.

## Real-size math at 345 ppi
- 1 cm is about 135.8 px, so A4 width (210 mm) is about 2852 px.
- The screen's short side is about 157 mm, so an A4 page at 100% does not fit the screen width in portrait, and shows only part of its height in landscape.
- Reported device DPI may be inaccurate, so a calibration setting is worth considering.

## Notes
- Some Xiaomi devices intercept three-finger gestures at system level; avoid relying on them.
- HyperOS may limit an app's refresh rate; check the rate the app actually gets.
