package info.kgeorgiy.ja.gordienko.bank;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RemotePerson extends UnicastRemoteObject implements Person {
    private final String firstName;
    private final String lastName;
    private final String passportNumber;

    public RemotePerson(String firstName,
                        String lastName,
                        String passportNumber,
                        int port) throws RemoteException {
        super(port);
        this.firstName = firstName;
        this.lastName = lastName;
        this.passportNumber = passportNumber;
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    @Override
    public String getPassportNumber() {
        return passportNumber;
    }
}
