package com.ath.atonyx

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ManagedPropsTest {

    // - test that default is returned when prop has never been set
    // - test that a change to usr is applied to cur when sys is never set
    // - test that a change to usr is applied to cur when sys is null
    @Test
    fun test1() {
        val default = false

        val props = ManagedProps()
        val p1 = Prop(props, default)

        // assert that nothing is set, and cur gets the default
        assert(p1.usr == null)
        assert(p1.sys == null)
        assert(p1.cur == default)

        // user sets their preference
        p1.usr = true

        // assert that cur adopts the user preference because sys does not override
        assert(p1.usr == true)
        assert(p1.sys == null)
        assert(p1.cur == true)

        // system overrides user
        p1.sys = false

        // assert that cur adopts the system override despite user preference
        assert(p1.usr == true)
        assert(p1.sys == false)
        assert(p1.cur == false)

        // system canceled override
        p1.sys = null

        // assert that cur adopts user preference now that system override is canceled
        assert(p1.usr == true)
        assert(p1.sys == null)
        assert(p1.cur == true)
    }


    // - test that a change to usr is NOT applied to cur when sys is NOT null
    // - test that a change to sys is applied to cur when usr is null
    // - test that a change to sys is applied to cur when usr is NOT null
    // - test that a cur reverts to usr when sys is reverted
    @Test
    fun test2() {
        val default = "A"

        val props = ManagedProps()
        val p1 = Prop(props, default)

        // assert that nothing is set, and cur gets the default
        assert(p1.usr == null)
        assert(p1.sys == null)
        assert(p1.cur == default)

        p1.sys = "B"

        // sys was applied to cur
        assert(p1.usr == null)
        assert(p1.sys == "B")
        assert(p1.cur == p1.sys)

        p1.usr = "C"

        // usr was not applied to cur
        assert(p1.usr == "C")
        assert(p1.sys == "B")
        assert(p1.cur == p1.sys)

        p1.sys = null

        // usr was applied to cur because sys was reverted
        assert(p1.usr == "C")
        assert(p1.sys == null)
        assert(p1.cur == "C")
    }

    // - test callback is called only if/when a change is applied to [ManagedProps.cur]
    // - test when change is applied
    // - test when change is not applied EX: change to usr, but there is a sys override
    @Test
    fun test3() {
        var callbackCount = 0

        val props = ManagedProps()
        val p1 = Prop(props, false) {
            callbackCount += 1
        }

        // assert that nothing is set, and cur gets the default
        assert(callbackCount == 0)

        // does not trigger a change. cur is unset, but defaults to false. however, cur is now set.
        p1.usr = false
        assert(callbackCount == 0)
        callbackCount = 0

        // trigger a change because cur was false
        p1.usr = true
        assert(callbackCount == 1)
        callbackCount = 0

        // does not trigger a change because usr already set
        p1.sys = true
        assert(callbackCount == 0)
        callbackCount = 0

        // does not trigger a change because sys overrides
        p1.usr = false
        assert(callbackCount == 0)
        callbackCount = 0

        // trigger a change as cur is true and adopts usr=false because sys do longer overrides
        p1.sys = null
        assert(callbackCount == 1)
        callbackCount = 0

    }

    // - test changing a property within a callback will mutate the property but will not call the callback again
    @Test
    fun test4() {
        var callbackCount = 0

        val props = ManagedProps()
        var verySilly: Prop<Int>? = null

        val p1 = Prop(props, 0) {
            callbackCount += 1
            verySilly?.sys = it.cur + 1 // trigger another change
        }

        verySilly = p1

        p1.sys = 1

        // property is mutated but callback does not get called again
        assert(p1.sys == 2)

        // try again to make sure the callback still works
        p1.sys = 1

        // callback still works
        assert(p1.sys == 2)

    }

}