package info.kgeorgiy.ja.gordienko.bank;

import java.rmi.*;

public interface Account extends Remote {
    /** Returns account identifier. */
    String getId() throws RemoteException;

    /** Returns amount of money in the account. */
    int getAmount() throws RemoteException;

    /** Sets amount of money in the account. */
    void setAmount(int amount) throws RemoteException;

    /** Adds money on the account. */
    void addMoney(int change) throws RemoteException;
}
