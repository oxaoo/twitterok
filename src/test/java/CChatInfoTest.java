import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.junit.Test;

import java.util.UUID;


import static org.junit.Assert.*;

public class CChatInfoTest
{
    private Logger log = LoggerFactory.getLogger(CChatInfoTest.class);

    @Ignore
    @Test
    public void equalsChatInfo()
    {
        log.info("*** equalsChatInfo ***");

        CChatInfo ci1 = new CChatInfo(0, 1);
        CChatInfo ci2 = new CChatInfo(1, 0);
        CChatInfo ci3 = new CChatInfo(1, 0);
        CChatInfo ci4 = new CChatInfo(2, 3);
        Object ci5 = new CChatInfo(2, 3);
        Object ci6 = new Object();
        CChatInfo ci7 = new CChatInfo(4, 5, UUID.randomUUID());
        CChatInfo ci8 = new CChatInfo(4, 5, UUID.randomUUID());
        CChatInfo ci9 = new CChatInfo(5, 4, UUID.randomUUID());
        CChatInfo ci10 = new CChatInfo(6, 7, UUID.randomUUID());
        CChatInfo ci11 = new CChatInfo(0, 1, UUID.randomUUID());

        assertEquals(ci1, ci2);
        assertEquals(ci2, ci3);
        assertNotEquals(ci3, ci4);
        assertEquals(ci4, ci5);
        assertNotEquals(ci5, ci6);
        assertNotEquals(ci1, ci6);
        assertEquals(ci7, ci8);
        assertEquals(ci8, ci9);
        assertNotEquals(ci9, ci10);
        assertEquals(ci11, ci1);
    }

    @Ignore
    @Test
    public void hashCodeChatInfo()
    {
        log.info("*** hashCodeChatInfo ***");

        CChatInfo ci1 = new CChatInfo(0, 1);
        CChatInfo ci2 = new CChatInfo(1, 0);
        CChatInfo ci3 = new CChatInfo(1, 0);
        CChatInfo ci4 = new CChatInfo(30, 12);
        Object ci5 = new CChatInfo(12, 30);

        assertEquals(ci1.hashCode(), ci2.hashCode());
        assertEquals(ci2.hashCode(), ci3.hashCode());
        assertEquals(ci4.hashCode(), ci5.hashCode());
    }

    @Ignore
    @Test
    public void uniqueUuid()
    {
        log.info("*** uniqueUuid ***");
        UUID testUuid = new CChatInfo(0, 0).getUuid();

        for(int i = 0; i < 1000000; i++)
            assertFalse(new CChatInfo(0, 0).getUuid().equals(testUuid));
    }
}
