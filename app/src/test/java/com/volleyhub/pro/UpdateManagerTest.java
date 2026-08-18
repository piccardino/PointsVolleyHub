package com.volleyhub.pro;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateManagerTest {

    @Test
    public void testIsNewerVersion() {
        // Tag with 'v' prefix
        assertTrue(UpdateManager.isNewerVersion("v1.1", "1.0"));
        assertTrue(UpdateManager.isNewerVersion("v2.0", "1.9"));
        assertTrue(UpdateManager.isNewerVersion("v1.0.1", "1.0"));
        assertTrue(UpdateManager.isNewerVersion("v1.0.0.1", "1.0.0"));
        assertTrue(UpdateManager.isNewerVersion("1.1", "1.0"));

        // Same versions
        assertFalse(UpdateManager.isNewerVersion("v1.0", "1.0"));
        assertFalse(UpdateManager.isNewerVersion("1.0", "1.0"));
        assertFalse(UpdateManager.isNewerVersion("v1.0.0", "1.0.0"));

        // Older remote versions
        assertFalse(UpdateManager.isNewerVersion("v0.9", "1.0"));
        assertFalse(UpdateManager.isNewerVersion("1.0", "1.1"));
        assertFalse(UpdateManager.isNewerVersion("v1.0.1", "1.1.0"));

        // Edge cases
        assertFalse(UpdateManager.isNewerVersion(null, "1.0"));
        assertFalse(UpdateManager.isNewerVersion("", "1.0"));
        assertTrue(UpdateManager.isNewerVersion("v1.0", null));
        assertTrue(UpdateManager.isNewerVersion("v1.0", ""));
    }
}
