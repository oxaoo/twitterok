import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class CClientTest
{
    private Logger log = LoggerFactory.getLogger(CClientTest.class);

    String host;
    int port;
    Date time;

    @Ignore
    @Before
    public void setup()
    {
        host = "localhost";
        port = 3200;
        time = Calendar.getInstance().getTime();
    }

    @Ignore
    @Test
    public void equalsClient()
    {
        log.info("*** equalsClient ***");

        assertNotEquals(new CClient(host, port, time), new CClient(host, port, time));
        assertNotEquals(new CClient(host, port, time), new CClient("192.168.0.1", 8081, Calendar.getInstance().getTime()));
        assertNotEquals(new CClient(host, port, time), new Object());

        CClient c = new CClient(host, port, time);
        assertEquals(c, c);
        assertNotEquals(c, null);
        assertEquals(c, (Object) c);
    }

    @Ignore
    @Test
    public void hashcodeClient()
    {
        log.info("*** hashcodeClient ***");

        CClient c = new CClient(host, port, time);

        for (int i = 0; i < Integer.MAX_VALUE; i++)
            assertNotEquals(c.hashCode(), new CClient(host, port, time).hashCode());
    }

    @Ignore
    @Test
    public void newClients() throws InterruptedException
    {
        log.info("*** newClients ***");

        int count = 1000;
        List<Thread> threads = new LinkedList<>();

        for (int i = 0; i < count; i++)
            threads.add(new Thread(() -> new CClient(host, port + 1, time)));

        for (Thread thread : threads)
            thread.start();

        for(Thread thread : threads)
            thread.join();

        assertEquals(count, CClient.getOnline());
    }

    @Ignore
    @Test
    public void getClient()
    {
        log.info("*** getClient ***");

        new CClient(host, 1, time);
        new CClient(host, 10, time);
        new CClient(host, 99, time);
        assertNotNull(CClient.getClient(host, 1));
        assertNotNull(CClient.getClient(host, 10));
        assertNotNull(CClient.getClient(host, 99));

        assertNull(CClient.getClient(0));
        assertNull(CClient.getClient(-10));
        assertNull(CClient.getClient(1000000));

        CClient c = new CClient(host, 9000, time);
        assertNotNull(c);
        CClient.unregisterClient(host, 9000);
        c = CClient.getClient(host, 9000);
        assertNull(c);

        CClient c1 = new CClient(host, 5000, time);
        int id = c1.getId();
        CClient c2 = CClient.getClient(id);
        assertEquals(c1, c2);

        CClient c3 = CClient.getClient(host, 5000);
        assertEquals(c2, c3);
    }

    @Ignore
    @Test
    public void privateChat()
    {
        log.info("*** privateChat ***");

        assertNull(CClient.getPrivateChat(-1));
        assertNull(CClient.getPrivateChat(0));
        assertNull(CClient.getPrivateChat(1000));

        CChatInfo ci1 = new CChatInfo(1, 2);
        assertTrue(CClient.addPrivateChat(ci1));
        UUID ci1Uuid = ci1.getUuid();
        CChatInfo ci2 = CClient.getPrivateChatByUuid(ci1Uuid.toString());
        assertNotNull(ci2);
        assertEquals(ci2, ci1);

        int ci1Index = CClient.indexPrivateChat(ci1);
        assertNotEquals(ci1Index, -1);
        CChatInfo ci3 = CClient.getPrivateChat(ci1Index);
        assertNotNull(ci3);
        assertEquals(ci3, ci1);
        assertEquals(ci3, ci2);

        CChatInfo ci4 = new CChatInfo(2, 1);
        assertFalse(CClient.addPrivateChat(ci4));

        List<UUID> uuidList = CClient.closePrivateChat(2);
        assertFalse(uuidList.isEmpty());

        uuidList = CClient.closePrivateChat(1);
        assertTrue(uuidList.isEmpty());

        assertTrue(CClient.addPrivateChat(ci4));

        int countPrivateChats = 50;
        for(int i = 0; i < countPrivateChats; i++)
            CClient.addPrivateChat(new CChatInfo(100, i));

        uuidList = CClient.closePrivateChat(100);
        assertEquals(uuidList.size(), countPrivateChats);

        assertTrue(CClient.closePrivateChat(-100).isEmpty());
        assertTrue(CClient.closePrivateChat(0).isEmpty());
    }

    @Ignore
    @Test
    public void unregisterClients() throws InterruptedException
    {
        log.info("*** unregisterClients ***");

        int count = 1000;
        List<Thread> threads = new LinkedList<>();
        List<Thread> unrThreads = new LinkedList<>();

        AtomicInteger ai = new AtomicInteger(0);
        for (int i = 0; i < count; i++)
            threads.add(new Thread(() -> new CClient(host, port + ai.incrementAndGet(), time)));


        for (Thread thread : threads)
            thread.start();

        for(Thread thread : threads)
            thread.join();

        assertEquals(count, CClient.getOnline());

        int offline = 500;
        ai.set(0);
        for (int i = 0; i < offline; i++)
            unrThreads.add(new Thread(() -> CClient.unregisterClient(host, port + ai.incrementAndGet())));

        for (Thread thread : unrThreads)
            thread.start();

        for(Thread thread : unrThreads)
            thread.join();

        assertEquals(count - offline, CClient.getOnline());
    }
}
