package org.decsync.sparss.utils;

import android.content.Context;
import android.support.test.runner.AndroidJUnit4;
import android.test.InstrumentationTestCase;

import org.decsync.sparss.MainApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class SampleAndroidTest extends InstrumentationTestCase {

    Context mContext;

    @Before
    public void setUp() {
        mContext = getInstrumentation().getContext();
        MainApplication.setContext(mContext);
    }

    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

}