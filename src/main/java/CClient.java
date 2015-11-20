import com.google.gson.Gson;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CClient
{
    private static AtomicInteger count = new AtomicInteger(0);
    private static AtomicInteger online = new AtomicInteger(0);
    private static Map<Integer, CClient> clients = new ConcurrentHashMap<Integer, CClient>();
    private static Map<String, Integer> addrMap = new ConcurrentHashMap<String, Integer>();

    private final int id;
    private final String host;
    private final int port;
    private final Date logontime;

    public CClient(String host, int port, Date time)
    {
        count.incrementAndGet();
        online.incrementAndGet();
        id = count.get();

        this.host = host;
        this.port = port;
        this.logontime = time;

        String addr = host.concat(":").concat(String.valueOf(port));
        addrMap.put(addr, id);

        clients.put(id, this);
    }

    public static boolean unregisterClient(String host, int port)
    {
        String addr = host.concat(":").concat(String.valueOf(port));
        if (addrMap.containsKey(addr))
        {
            int id = addrMap.get(addr);
            clients.remove(id);
            online.decrementAndGet();
            return true;
        }

        return false;
    }

    public static int getOnline()
    {
        return online.get();
    }

    @Override
    public int hashCode()
    {
        return host.hashCode() ^ port;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;

        if (obj == this)
            return true;

        if (!(obj instanceof CClient))
            return false;

        CClient c = (CClient) obj;
        if (this.id == c.id
                && this.host.equals(c.host)
                && this.port == c.port
                && this.logontime.getTime() == c.logontime.getTime())
            return true;

        return false;
    }


    //public static String toJson()
    public static Collection<CClient> getOnlineList()
    {
        return clients.values();
    }

}
