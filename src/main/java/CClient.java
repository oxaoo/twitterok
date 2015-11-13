import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Alexander on 13.11.2015.
 */
public class CClient
{
    public static AtomicInteger count = new AtomicInteger(0);
    private final int id;
    private final String host;
    private final int port;

    private CClient(String host, int port)
    {
        count.incrementAndGet();
        id = count.get();

        this.host = host;
        this.port = port;
    }
}
