package info.kgeorgiy.ja.gordienko.bank;

import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class Tests {
    private static RemoteBank bank;

    @BeforeAll
    static void setup() {
        Server.main();
        bank = new RemoteBank(0);
    }

    @Test
    @DisplayName("Create remote person and validate info")
    void testCreateAndValidateRemotePerson() throws RemoteException {
        final String passportNumber = "111111";
        Person person = bank.getPersonOfType(passportNumber, Bank.PersonType.REMOTE);
        assertNull(person, "Person with passport number " + passportNumber + " does not exist yet");
        final String firstName = "Konstantin";
        final String lastName = "Gordienko";
        person = bank.createPerson(firstName, lastName, passportNumber);
        assertEquals(firstName, person.getFirstName());
        assertEquals(lastName, person.getLastName());
        assertEquals(passportNumber, person.getPassportNumber());

        Person remotePerson = bank.getPersonOfType(passportNumber, Bank.PersonType.REMOTE);
        assertNotNull(remotePerson, "Person with passport number " + passportNumber + " already exists");
        assertEquals(person.getFirstName(), remotePerson.getFirstName());
        assertEquals(person.getLastName(), remotePerson.getLastName());
        assertEquals(person.getPassportNumber(), remotePerson.getPassportNumber());
    }

    @Test
    @DisplayName("Create local person and validate info")
    void testCreateAndValidateLocalPerson() throws RemoteException {
        final String firstName = "Georgiy";
        final String lastName = "Korneev";
        final String passportNumber = "222222";
        bank.createPerson(firstName, lastName, passportNumber);
        Person localPerson = bank.getPersonOfType(passportNumber, Bank.PersonType.LOCAL);
        assertNotNull(localPerson);
        assertEquals(firstName, localPerson.getFirstName());
        assertEquals(lastName, localPerson.getLastName());
        assertEquals(passportNumber, localPerson.getPassportNumber());
    }

    @Test
    @DisplayName("Check local account isolation")
    void testLocalSnapshotOfRemotePerson() throws RemoteException {
        final String firstName = "Mike";
        final String lastName = "Mirzayanov";
        final String passportNumber = "333333";
        final String subId = "2";
        Person person = bank.createPerson(firstName, lastName, passportNumber);
        Account remoteAccount = bank.createAccount(subId, person);
        remoteAccount.setAmount(100);

        // creating local copy of this person
        Person localPerson = bank.getPersonOfType(passportNumber, Bank.PersonType.LOCAL);
        Account localAccount = bank.getAccount(subId, localPerson);
        assertEquals(remoteAccount.getAmount(), localAccount.getAmount());

        remoteAccount.setAmount(200);
        assertEquals(200, remoteAccount.getAmount());
        assertEquals(100, localAccount.getAmount());

        localAccount.setAmount(300);
        assertEquals(300, localAccount.getAmount());

        // check if Changes to accounts made through RemotePerson
        // are immediately applied globally, while changes made through
        // LocalPerson are only applied locally to that particular LocalPerson
        assertEquals(200, bank.getAccount(subId, person).getAmount());
    }

    @Test
    @DisplayName("Check local person isolation")
    void testLocalPersonIsolation() throws RemoteException {
        final String firstName = "Andrew";
        final String lastName = "Stankevich";
        final String passportNumber = "466789";
        final String subId = "3";
        Person remotePerson1 = bank.createPerson(firstName, lastName, passportNumber);
        Account remoteAccount1 = bank.createAccount(subId, remotePerson1);
        remoteAccount1.setAmount(150);

        // new RemotePerson should see the changes immediately
        Person remotePerson2 = bank.getPersonOfType(passportNumber, Bank.PersonType.REMOTE);
        Account remoteAccount2 = bank.getAccount(subId, remotePerson2);
        assertEquals(150, remoteAccount2.getAmount(), "RemotePerson should see updated balance immediately");

        Person localPerson = bank.getPersonOfType(passportNumber, Bank.PersonType.LOCAL);
        Account localAccount = bank.getAccount(subId, localPerson);
        assertEquals(150, localAccount.getAmount(), "New LocalPerson should see the balance at snapshot time");

        remoteAccount1.setAmount(250);
        assertEquals(250, bank.getAccount(subId, remotePerson1).getAmount(), "Global change should be applied to the bank");

        // old local snapshot should not see the changes
        assertEquals(150, localAccount.getAmount(), "Existing LocalPerson should not see subsequent changes");

        // changing local account state should not affect remote account
        localAccount.setAmount(350);
        assertEquals(350, localAccount.getAmount(), "Local changes should be applied locally");
        assertEquals(250, bank.getAccount(subId, remotePerson1).getAmount(), "Global account should not be changed by LocalPerson");
    }

    @Test
    @DisplayName("Check person with multiple accounts")
    void testMultipleAccounts() throws RemoteException {
        final String firstName = "Gennady";
        final String lastName = "Korotkevich";
        final String passportNumber = "121212";
        Person person = bank.createPerson(firstName, lastName, passportNumber);
        bank.createAccount("12", person);
        bank.createAccount("13", person);
        Set<String> ids = bank.getPersonAccounts(person);
        assertTrue(ids.contains("12"));
        assertTrue(ids.contains("13"));
    }

    @Test
    @DisplayName("Check negative balance change")
    void testNegativeBalanceChange() throws RemoteException {
        final String firstName = "First";
        final String lastName = "Last";
        final String passportNumber = "987654";
        final String accountId = "98";

        PrintStream originalOut = System.out;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        try {
            Client.main(firstName, lastName, passportNumber, accountId, "1000");
            Client.main(firstName, lastName, passportNumber, accountId, "-2000");
        } finally {
            System.setOut(originalOut);
        }
        String output = outContent.toString();
        System.out.println(output);
        assertTrue(output.contains("Not enough money on the account."));
        assertEquals("1000", getAmountFromOutput(output));
    }

    @Test
    @DisplayName("Concurrent updates on RemoteAccount")
    void testRemoteAccountConcurrency() throws RemoteException, InterruptedException {
        RemoteAccount remoteAccount = new RemoteAccount("concurrent", 0);
        multithreadingTest(remoteAccount, 10, 1_000);
    }

    @Test
    @DisplayName("Concurrent updates on LocalPerson's LocalAccount")
    void testLocalPersonAccountConcurrency() throws RemoteException, InterruptedException {
        LocalAccount localAccount = new LocalAccount("local1");
        Map<String, LocalAccount> map = new ConcurrentHashMap<>();
        map.put(localAccount.getId(), localAccount);
        LocalPerson person = new LocalPerson("First", "Last", "444444", map);
        Account accountFromPerson = person.getAccountById(localAccount.getId());
        multithreadingTest(accountFromPerson, 8, 2_500);
    }

    private String getAmountFromOutput(String output) {
        StringBuilder number = new StringBuilder();
        int i = output.length() - 1;
        while (output.charAt(i) != ' ') {
            number.append(output.charAt(i));
            --i;
        }
        return number.reverse().toString();
    }

    private void multithreadingTest(Account account, int threads, int count) throws InterruptedException, RemoteException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < count; j++) {
                        account.addMoney(1);
                    }
                } catch (InterruptedException | RemoteException e) {
                    fail("Error occurred in thread: " + e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.close();
        assertEquals((long) threads * count, account.getAmount(),
                "Total balance should be equal to threads * count");
    }
}
