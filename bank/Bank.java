package info.kgeorgiy.ja.gordienko.bank;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

public interface Bank extends Remote {
    enum PersonType {
        LOCAL,
        REMOTE
    }

    /**
     * Creates a new account with specified identifier if it does not already exist.
     * @param id account id
     * @return created or existing account.
     */
    Account createAccount(String id, Person person) throws RemoteException;

    /**
     * Returns account by identifier.
     * @param id account id
     * @return account with specified identifier or {@code null} if such account does not exist.
     */
    Account getAccount(String id, Person person) throws RemoteException;

    /**
     * Creates a new person with specified information if it does not already exist.
     * @param firstName person's first name
     * @param lastName person's last name
     * @param passportNumber person's passport number
     * @return new person if person with specified passport number does not exist yet, and existing person otherwise.
     */
    Person createPerson(String firstName,
                        String lastName,
                        String passportNumber) throws RemoteException;

    /**
     * Returns person of specified type with specified passport number.
     * @param passportNumber passport number to search for
     * @param personType the type of person to return:
     *                        {@code PersonType.REMOTE} for a live RMI stub,
     *                        {@code PersonType.LOCAL} for a serialized snapshot
     * @return {@link Person} of specified type with specified passport number, or {@code null} if such person does not exist.
     */
    Person getPersonOfType(String passportNumber, PersonType personType) throws RemoteException;

    /**
     * Returns the set of account sub-identifiers associated with the given person.
     * @param person {@link Person} whose accounts to be retrieved
     * @return a {@link Set} of {@link String} account non-null sub-IDs (each without the passport prefix).
     */
    Set<String> getPersonAccounts(Person person) throws RemoteException;
}
