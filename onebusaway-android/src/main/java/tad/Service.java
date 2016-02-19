/*
 *
 */
package tad;


/**
 *
 */
public class Service {

    private int idService;

    public void setIdService(int idService)  {
        this.idService = idService;
    }

    public int getIdService() {
        return idService;
    }

    private long creatorIdRole;

    public void setCreatorIdRole(long creatorIdRole)  {
        this.creatorIdRole = creatorIdRole;
    }

    public long getCreatorIdRole() {
        return creatorIdRole;
    }

    private long passengerIdRole;

    public void setPassengerIdRole(long passengerIdRole)  {
        this.passengerIdRole = passengerIdRole;
    }

    public long getPassengerIdRole() {
        return passengerIdRole;
    }

    private String serviceName;

    public void setServiceName(String serviceName)  {
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
