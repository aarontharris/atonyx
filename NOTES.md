# References

The Onyx Docs appear out of date, however, some docs are still valid
and even where docs are incorrect, they still give us a use-philosophy which helps point in
the right direction in some cases.

## All Docs

https://github.com/onyx-intl/OnyxAndroidDemo/tree/master/doc

## Read all of these

At least skim, it's important to have some registry in your brain of facilities that exist
so that we don't unnecessarily reinvent any wheels.
https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EPD-Screen-Update.md
https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EPD-Update-Mode.md
https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EPD-Touch.md
https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EpdDeviceManager.md
https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/Onyx-Pen-SDK.md
https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/Scribble-API.md
https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/Scribble-TouchHelper-API.md

TLDR; Digest
- NOTE: "Touch" refers to pen or finger input within the drawing region
- EpdController - Set Update Mode, can improve perf
- EpdController - Can Enable/Disable touch to all, or to select regions
- EpdDeviceManager - Set full/partial screen update intervals
- EpdDeviceManager - Can Enable/Disable enter/exit animation
- 

## DPI & Screen Resolution per device

https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/DPI-and-DP-for-each-devices.asciidoc

## Supported Deep Links

https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/AppOpenGuide.md

# Excuses

I am not an Onyx expert.

Onyx Documentation is minimal and it appears the source is obfuscated.

Wee!

# How does rendering work

Check out AtOnyx.kt for src.

## Assumptions from Observations

#### Writing the strokes

It seems that Onyx automagically takes care of writing pen stokes to the surface's canvas's bitmap.
Our job is to record these strokes so that they can be later restored for various cases.
This is done by reapplying the recorded pen strokes to a local bitmap via a canvas backed by that
bitmap, then queueing a job for Onyx to write the bitmap to the surface's canvas's bitmap. A little
like double-buffering but more silly.

# TODO

## Ideas from reading docs
- Look at EpdController update mode, this may improve performance for note taking

## Bugs

#### Remember if pen was enabled when clear/refresh

When we clear/refresh/eraseall/etc we must disablePen() then enablePen()
but what if the user had the pen disabled? now suddenly it is enabled.

#### strokes after clear - refresh, are cleared faster than others.

REPRO:

- Draw A
- Clear All
- Refresh
- Draw B
- Clear All
  OBSERV:
- B erased first, then A.

#### RadioButton clickarea seems off

Not sure why. For now I changed to a checkbox.

#### drawing blanks after disable pen ?

- draw zero
- pressure off
- observe: blank
- draw one
- pressure on
- observe: blank
- refresh
- 0 & 1
