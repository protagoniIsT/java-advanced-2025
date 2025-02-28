package info.kgeorgiy.ja.gordienko.student;

import info.kgeorgiy.java.advanced.student.GroupName;
import info.kgeorgiy.java.advanced.student.Student;
import info.kgeorgiy.java.advanced.student.StudentQuery;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class StudentDB implements StudentQuery {

    private static final Comparator<Student> NAME_COMPARATOR = Comparator.comparing(Student::firstName)
                                                                         .thenComparing(Student::lastName)
                                                                         .thenComparing(Student::groupName);

    private <T, R> T getProperties(Collection<Student> students,
                                   Function<Student, R> mapper,
                                   Collector<? super R, ?, T> collector) {
        return students.stream()
                .map(mapper)
                .collect(collector);
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return getProperties(students, Student::firstName, Collectors.toList());
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return getProperties(students, Student::lastName, Collectors.toList());
    }

    @Override
    public List<GroupName> getGroupNames(List<Student> students) {
        return getProperties(students, Student::groupName, Collectors.toList());
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return getProperties(students, (s) -> s.firstName() + " " + s.lastName(), Collectors.toList());
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return getProperties(students, Student::firstName, Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return students.stream()
                .max(Student::compareTo)
                .map(Student::firstName)
                .orElse("");
    }


    private List<Student> sortStudentsBy(Collection<Student> students, Comparator<? super Student> comparator) {
        return students.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return sortStudentsBy(students, Student::compareTo);
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortStudentsBy(students, NAME_COMPARATOR);
    }


    private <T> T findStudentsBy(Collection<Student> students,
                                 Predicate<? super Student> predicate,
                                 Collector<? super Student, ?, T> collector) {
        return students.stream()
                .filter(predicate)
                .sorted(NAME_COMPARATOR)
                .collect(collector);
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return findStudentsBy(students, s -> s.firstName().equals(name), Collectors.toList());
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return findStudentsBy(students, s -> s.lastName().equals(name), Collectors.toList());
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return findStudentsBy(students, s -> s.groupName().equals(group), Collectors.toList());
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return findStudentsBy(students, s -> s.groupName().equals(group),
                Collectors.toMap(Student::lastName, Student::firstName, BinaryOperator.minBy(String::compareTo)));
    }
}
