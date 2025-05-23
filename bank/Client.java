package info.kgeorgiy.ja.gordienko.bank;


import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public final class Client {
    /** Utility class. */
    private Client() {}

    public static void main(final String... args) throws RemoteException {
        final Bank bank;
        try {
            bank = (Bank) Naming.lookup("//localhost/bank");
        } catch (final NotBoundException e) {
            System.out.println("Bank is not bound");
            return;
        } catch (final MalformedURLException e) {
            System.out.println("Bank URL is invalid");
            return;
        }

        String firstName, lastName, passportNumber, accountId;
        int balanceChange;

        try {
            firstName = args[0];
            lastName = args[1];
            passportNumber = args[2];
            accountId = args[3];
            balanceChange = Integer.parseInt(args[4]);
        } catch (Exception e) {
            System.err.println("Usage: <firstName> <lastName> <passportNumber> <accountId> <balanceChange>");
            return;
        }

        Person person = bank.getPersonOfType(passportNumber, Bank.PersonType.REMOTE);
        if (person == null) {
            person = bank.createPerson(firstName, lastName, passportNumber);
            System.out.println("Created new person");
        }
        String existingPersonFirstName = person.getFirstName();
        String existingPersonLastName = person.getLastName();
        if (!existingPersonFirstName.equals(firstName) || !existingPersonLastName.equals(lastName)) {
            System.out.printf("Invalid first name and last name for person with passport number %s", passportNumber);
            return;
        }
        if (!bank.getPersonAccounts(person).contains(accountId)) {
            bank.createAccount(accountId, person);
        }

        Account account = bank.getAccount(accountId, person);

        System.out.printf("Bank account information:%nID: %s%nCurrent balance: %s%n", account.getId(), account.getAmount());

        if (account.getAmount() + balanceChange < 0) {
            System.out.printf("Not enough money on the account. Balance: %s", account.getAmount());
        } else {
            System.out.println("Performing operation...");
            account.addMoney(balanceChange);
            System.out.printf("Operation performed successfully. Balance: %s%n", account.getAmount());
        }
    }
}
