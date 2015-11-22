
public class CChatInfo
{
    public final int fromId;
    public final int toId;
    public final String address;

    public final String toIdHost;
    public final int toIdPort;
    public final boolean creator; //TODO: remove creator.

    public CChatInfo(int fromId, int toId, String address, String toIdHost, int toIdPort, boolean creator)
    {
        this.fromId = fromId;
        this.toId = toId;
        this.address = address;
        this.toIdHost = toIdHost;
        this.toIdPort = toIdPort;
        this.creator = creator;
    }
}
