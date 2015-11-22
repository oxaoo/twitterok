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

    public final transient CPrivateChat privateChat;

    private final int id;
    private final String host;
    private final int port;
    private final long logontime;
    private final transient UUID uuid;

    public CClient(String host, int port, Date time)
    {
        count.incrementAndGet();
        online.incrementAndGet();
        id = count.get();
        uuid = UUID.randomUUID();
        privateChat = new CPrivateChat(id);

        this.host = host;
        this.port = port;
        this.logontime = time.getTime();

        String addr = host.concat(":").concat(String.valueOf(port));
        addrMap.put(addr, id);

        clients.put(id, this);
    }

    public static CClient unregisterClient(String host, int port)
    {
        String addr = host.concat(":").concat(String.valueOf(port));
        if (addrMap.containsKey(addr))
        {
            int id = addrMap.get(addr);
            CClient unregClient = clients.remove(id);
            addrMap.remove(addr);
            online.decrementAndGet();
            return unregClient;
        }

        return null;
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
                && this.logontime == c.logontime
                && this.uuid == c.uuid)
            return true;

        return false;
    }


    //public static String toJson()
    public static Collection<CClient> getOnlineList()
    {
        return clients.values();
    }

    @Override
    public String toString()
    {
        return "CClient{" +
                "id=" + id +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", logontime=" + logontime +
                ", uuid=" + uuid +
                '}';
    }

    public static CClient getClient(String host, int port)
    {
        String addr = host.concat(":").concat(String.valueOf(port));
        if (addrMap.containsKey(addr))
        {
            int id = addrMap.get(addr);
            return clients.get(id);
        }

        return null;
    }

    public UUID getUuid()
    {
        return uuid;
    }

    public int getId()
    {
        return id;
    }

    public static CClient getClient(int id)
    {
        if (clients.containsKey(id))
            return clients.get(id);
        else
            return null;
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }
}
