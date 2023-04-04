package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CollapsingSet implements Set<Integer> {

    private final Set<Integer> set;
    private int floor = 0;

    public CollapsingSet() {
        this.set = new HashSet<>();
    }

    public CollapsingSet(Set<Integer> s) {
        this.set = s;
    }

    @Override
    public int size() {
        return floor + set.size();
    }

    @Override
    public boolean isEmpty() {
        return this.size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof Integer i)) return false;
        return i <= this.floor || this.set.contains(i);
    }

    private Set<Integer> getFullSet() {
        // inefficient
        Set<Integer> s = new HashSet<>();
        if (this.floor > 0)
            s = IntStream.rangeClosed(1, floor).boxed().collect(Collectors.toCollection(HashSet::new));
        s.addAll(this.set);
        return s;
    }

    @Override
    public Iterator<Integer> iterator() {
        return this.getFullSet().iterator();
    }

    @Override
    public Object[] toArray() {
        return this.getFullSet().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return this.getFullSet().toArray(a);
    }

    @Override
    public boolean add(Integer integer) {
        if (integer == (floor + 1)) {
            int newFloor = integer;
            while (this.set.contains(newFloor)) {
                this.set.remove(newFloor);
                newFloor++;
            }
            this.floor = newFloor;
            // should probably check the return value of the remove in the while
            return true;
        } else return this.set.add(integer);
    }

    @Override
    public boolean remove(Object o) {
        // not used
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        // not used
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends Integer> c) {
        c.forEach(this::add);
        return true;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        // not used
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        // not used
        return false;
    }

    @Override
    public void clear() {
        this.floor = 0;
        this.set.clear();
    }
}