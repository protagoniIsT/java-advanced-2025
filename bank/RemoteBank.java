package info.kgeorgiy.ja.gordienko.bank;


import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class RemoteBank implements Bank {
    private final int port;
    private final ConcurrentMap<String, Account> accounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Person> persons = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> accountsByPassport = new ConcurrentHashMap<>();

    public RemoteBank(final int port) {
        this.port = port;
    }

    @Override
    public Account createAccount(final String subId, Person person) throws RemoteException {
        final String passport = person.getPassportNumber();
        final String accountId = passport + ":" + subId;
        Account account = accounts.computeIfAbsent(accountId, id -> {
            try {
                return new RemoteAccount(id, port);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        });
        accountsByPassport.computeIfAbsent(passport, k -> new ConcurrentSkipListSet<>()).add(subId);
        return account;
    }

    @Override
    public Account getAccount(String id, Person person) throws RemoteException {
        String accountId = person.getPassportNumber() + ":" + id;
        Account account = accounts.get(accountId);
        if (account != null) {
            if (person instanceof LocalPerson) {
                account = ((LocalPerson) person).getAccountById(id);
            } else {
                return account;
            }
        }
        return account;
    }

    @Override
    public Person createPerson(String firstName,
                               String lastName,
                               String passportNumber) throws RemoteException {
        if (persons.get(passportNumber) == null) {
            persons.put(passportNumber, new RemotePerson(firstName, lastName, passportNumber, port));
            accountsByPassport.put(passportNumber, new ConcurrentSkipListSet<>());
        }
        return persons.get(passportNumber);
    }

    @Override
    public Person getPersonOfType(String passportNumber, PersonType personType) throws RemoteException {
        switch (personType) {
            case REMOTE -> {
                return getRemotePerson(passportNumber);
            }
            case LOCAL -> {
                return getLocalPerson(passportNumber);
            }
            default -> {
                System.err.println("Unknown person type: " + personType);
                return null;
            }
        }
    }

    private Person getRemotePerson(String passportNumber) throws RemoteException {
        return persons.get(passportNumber);
    }

    private Person getLocalPerson(String passportNumber) throws RemoteException {
        Person person = persons.get(passportNumber);
        Set<String> personAccounts = getPersonAccounts(person);
        Map<String, LocalAccount> localAccounts = new ConcurrentHashMap<>();
        for (String accountId : personAccounts) {
            Account acc = getAccount(accountId, person);
            localAccounts.put(accountId, new LocalAccount(acc.getId(), acc.getAmount()));
        }
        return new LocalPerson(person.getFirstName(), person.getLastName(), person.getPassportNumber(), localAccounts);
    }

    @Override
    public Set<String> getPersonAccounts(Person person) throws RemoteException {
        if (person instanceof LocalPerson) {
            return ((LocalPerson) person).getAccounts();
        }
        return accountsByPassport.get(person.getPassportNumber());
    }
}
