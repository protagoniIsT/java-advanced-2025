package info.kgeorgiy.ja.gordienko.bank;


import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RemoteAccount extends UnicastRemoteObject implements Account {
    private final String id;
    private int amount;

    public RemoteAccount(final String id, int port) throws RemoteException {
        super(port);
        this.id = id;
        amount = 0;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public synchronized int getAmount() {
        return amount;
    }

    @Override
    public synchronized void setAmount(final int amount) {
        this.amount = amount;
    }

    @Override
    public synchronized void addMoney(int change) throws RemoteException {
        this.amount += change;
    }
}
