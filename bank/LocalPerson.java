package info.kgeorgiy.ja.gordienko.bank;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;

public class LocalPerson implements Person, Serializable {
    private final String firstName;
    private final String lastName;
    private final String passportNumber;
    private final Map<String, LocalAccount> accounts;

    public LocalPerson(String firstName,
                       String lastName,
                       String passportNumber,
                       Map<String, LocalAccount> accounts) throws RemoteException  {
        this.firstName = firstName;
        this.lastName = lastName;
        this.passportNumber = passportNumber;
        this.accounts = accounts;
    }

    @Override
    public String getFirstName() throws RemoteException {
        return firstName;
    }

    @Override
    public String getLastName() throws RemoteException {
        return lastName;
    }

    @Override
    public String getPassportNumber() throws RemoteException {
        return passportNumber;
    }

    public Account getAccountById(String id) {
        return accounts.get(id);
    }

    public Set<String> getAccounts() throws RemoteException {
        return accounts.keySet();
    }
}
