import java.util.UUID;

public class CChatInfo
{
    private final int fromId;
    private final int toId;
    private UUID uuid;

    public CChatInfo(int fromId, int toId, UUID uuid)
    {
        this.fromId = fromId;
        this.toId = toId;
        this.uuid = uuid;
    }

    public CChatInfo(int fromId, int toId)
    {
        this.fromId = fromId;
        this.toId = toId;
        this.uuid = UUID.randomUUID();
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

        return (this.fromId == c.fromId && this.toId == c.toId
                || this.fromId == c.toId && this.toId == c.fromId)
                && this.fromId != this.toId;
    }

    @Override
    public int hashCode()
    {
        return fromId ^ toId;
    }

    @Override
    public String toString()
    {
        return "CChatInfo{" +
                "fromId=" + fromId +
                ", toId=" + toId +
                ", uuid='" + uuid + '\'' +
                '}';
    }

    public UUID getUuid()
    {
        return uuid;
    }

    public int getFromId()
    {
        return fromId;
    }

    public int getToId()
    {
        return toId;
    }

    public void genAddress()
    {
        this.uuid = UUID.randomUUID();
    }
}
