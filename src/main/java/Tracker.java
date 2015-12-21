import java.util.*;

public abstract class Tracker<T, N extends Tracker.Node<T, N>> {
    public boolean track(N node, int rootLen) {
        if (node.children == null) {
            if (node.trunk.length < 2)
                return false;

            trackTrunk(node, rootLen);
        } else {
            if (node.trunk.length < 2)
                return trackChildren(node, rootLen);

            if (isTrunkTurn(node) || !trackChildren(node, rootLen))
                trackTrunk(node, rootLen);
        }

        return true;
    }

    private boolean trackChildren(final N node, int rootLen) {
        Collections.sort(node.children, new Comparator<N>() {
            @Override
            public int compare(N n1, N n2) {
                double q1 = getNodeValuation(n1, node);
                double q2 = getNodeValuation(n2, node);
                return Double.compare(q2, q1);
            }
        });
        for (N bChild : node.children) {
            int oldCount = bChild.stateCount;
            if (!track(bChild, node.trunk.length + rootLen))
                continue;

            node.stateCount += bChild.stateCount - oldCount;
            node.update(false);
            return true;
        }

        return false;
    }

    private void trackTrunk(N node, int rootLen) {
        int trunkPoint = selectTrunkPoint(node);
        T endState = node.trunk.get(trunkPoint);
        trunkPoint++;

        N normalChild = buildNormalNode(node, endState, trunkPoint);
        normalChild.trunk = new ArrayView<>(node.trunk, trunkPoint);
        normalChild.children = node.children;
        normalChild.stateCount = node.stateCount - trunkPoint;
        normalChild.update(true);

        N[] newChildren = buildTrunkChildren(node, endState, rootLen + trunkPoint);
        node.children = new ArrayList<>(newChildren.length + 1);
        for (N child : newChildren) {
            if (child != null) {
                node.stateCount += child.stateCount;
                node.children.add(child);
            }
        }

        node.children.add(normalChild);
        node.trunk = new ArrayView<>(node.trunk, 0, trunkPoint);

        node.update(true);
    }

    protected abstract int selectTrunkPoint(N node);
    protected abstract boolean isTrunkTurn(N node);
    protected abstract N[] buildTrunkChildren(N node, T start, int rootLen);
    protected abstract N buildNormalNode(N parent, T start, int trunkPoint);
    protected abstract double getNodeValuation(N node, N parent);

    public abstract static class Node<T, N extends Node> {
        protected ArrayView<T> trunk;
        protected List<N> children;
        protected int stateCount;

        public abstract void update(boolean updateTrunk);
    }

    public static class ArrayView<T> {
        private final T[] array;
        private final int start;
        public final int length;

        public ArrayView(T[] array, int len) {
            if (len > array.length)
                throw new IllegalArgumentException("len > array.length");

            this.array = array;
            this.start = 0;
            this.length = len;
        }
        public ArrayView(ArrayView<T> array, int start) {
            this(array, start, array.length - start);
        }
        public ArrayView(ArrayView<T> array, int start, int length) {
            if (start < 0 || length < 0)
                throw new IllegalArgumentException("start < 0 || length < 0");

            if ((start+length) > array.length)
                throw new IllegalArgumentException("(start+length) > array.length");

            this.array = array.array;
            this.start = array.start + start;
            this.length = length;
        }

        public T get(int index) {
            if (index < 0 || index >= length)
                throw new ArrayIndexOutOfBoundsException(index);

            return array[start + index];
        }
    }
}
