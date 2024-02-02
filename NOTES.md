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

