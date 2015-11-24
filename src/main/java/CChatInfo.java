import java.util.UUID;

public class CChatInfo
{
    private final int fromId;
    private final int toId;
    private String address;

    public CChatInfo(int fromId, int toId, String address)
    {
        this.fromId = fromId;
        this.toId = toId;
        this.address = address;
    }

    public CChatInfo(int fromId, int toId)
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

        if (!(obj instanceof CChatInfo))
            return false;

        CChatInfo c = (CChatInfo) obj;

        if ((this.fromId == c.fromId && this.toId == c.toId
            || this.fromId == c.toId && this.toId == c.fromId)
                && this.fromId != this.toId)
            //&& this.address.equals(c.address))
            return true;
        else return false;
    }

    @Override
    public int hashCode()
    {
        return fromId ^ toId;// ^ address.hashCode();
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public void genAddress()
    {
        this.address = UUID.randomUUID().toString();
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

    @Override
    public String toString()
    {
        return "CChatInfo{" +
                "fromId=" + fromId +
                ", toId=" + toId +
                ", address='" + address + '\'' +
                '}';
    }
}
