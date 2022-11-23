package od.metanome.sorting.partitions;

/**
 * Created by Mehdi on 7/1/2016.
 */
public class RowIndexedDoubleValue extends RowIndexedValue {
    public final Double value;

    public RowIndexedDoubleValue(final long index, final Double value) {
        this.index = index;
        this.value = value;
    }
}
