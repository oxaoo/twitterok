import java.util.UUID;

public class CChat
{
    private final int fromId;
    private final int toId;
    private final String address;

    public CChat(int fromId, int toId, String address)
    {
        this.fromId = fromId;
        this.toId = toId;
        this.address = address;
    }

    public CChat(int fromId, int toId)
    {
        this.fromId = fromId;
        this.toId = toId;
        this.address = UUID.randomUUID().toString();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;

        if (obj == this)
            return true;

        if (!(obj instanceof CChat))
            return false;

        CChat c = (CChat) obj;

        if ((this.fromId == c.fromId && this.toId == c.toId)
            || (this.fromId == c.toId && this.toId == c.fromId && this.fromId != this.toId))
            //&& this.address.equals(c.address))
            return true;
        else return false;
    }

    @Override
    public int hashCode()
    {
        return fromId ^ toId;// ^ address.hashCode();
    }

    public String getAddress()
    {
        return address;
    }

    public int getFromId()
    {
        return fromId;
    }

    public int getToId()
    {
        return toId;
    }
}
