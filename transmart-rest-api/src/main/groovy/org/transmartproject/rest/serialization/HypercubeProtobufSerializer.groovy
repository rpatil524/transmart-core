/* Copyright © 2017 The Hyve B.V. */
package org.transmartproject.rest.serialization

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.PeekingIterator
import grails.util.Pair
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.transmartproject.core.multidimquery.IdentityProperty
import org.transmartproject.core.multidimquery.Dimension
import org.transmartproject.core.multidimquery.Hypercube
import org.transmartproject.core.multidimquery.HypercubeValue
import org.transmartproject.core.multidimquery.Property
import org.transmartproject.rest.hypercubeProto.ObservationsProto.Type as ProtoType
import org.transmartproject.rest.hypercubeProto.ObservationsProto.Error

import javax.annotation.Nonnull

import static org.transmartproject.rest.hypercubeProto.ObservationsProto.*

@Slf4j
@CompileStatic
class HypercubeProtobufSerializer extends HypercubeSerializer {

    protected Hypercube cube
    protected Dimension packedDimension
    protected boolean packingEnabled
    protected OutputStream out

    private PeekingIterator<HypercubeValue> iterator
    private List<Dimension> inlineDims
    private List<Dimension> indexedDims


    protected List<DimensionDeclaration> getDimensionsDefs() {
        cube.dimensions.collect { Dimension dim ->
            DimensionDeclaration.Builder builder = DimensionDeclaration.newBuilder()

            builder.name = dim.name
            if (!dim.density.isDense) {
                // Sparse dimensions are inlined, dense dimensions are referred to by indexes
                // (referring to objects in the footer message).
                builder.inline = true
            }
            if (dim == packedDimension) {
                builder.packed = true
            }
            builder.type = dim.elementsSerializable ? Type.get(dim.elementType).protobufType : ProtoType.OBJECT

            if(!dim.elementsSerializable) {
                for(field in dim.elementFields.values()) {
                    builder.addFields FieldDefinition.newBuilder().with {
                        name = field.name
                        type = Type.get(field.type).protobufType
                        assert type != ProtoType.OBJECT, "Nested compound properties on dimension elements are not supported"
                        build()
                    }
                }
            }

            builder.build()
        }
    }

    protected Header buildHeader() {
        Header.newBuilder().with {
            addAllDimensionDeclarations(dimensionsDefs)
            if(!iterator.hasNext()) last = true
            build()
        }
    }

    protected Cell createCell(HypercubeValue value) {
        def builder = Cell.newBuilder()
        if (value.value != null) {
            if (value.value instanceof Number) {
                builder.numericValue = ((Number) value.value).doubleValue()
            } else {
                builder.stringValue = value.value
            }
        }
        for (Dimension d in indexedDims) {
            Integer idx = value.getDimElementIndex(d)
            // convert to 1-based values, 0 means not present
            builder.addDimensionIndexes(idx == null ? 0 : idx+1)
        }
        for (int i=0; i<inlineDims.size(); i++) {
            Dimension dim = inlineDims[i]
            def dimElement = value[dim]
            if(dimElement != null) {
                builder.addInlineDimensions(buildDimensionElement(dim, dimElement))
            } else {
                // these values are 1-based!
                builder.addAbsentInlineDimensions(i+1)
            }
        }
        if(!iterator.hasNext()) builder.last = true
        builder.build()
    }

    private Value buildValue(@Nonnull value) {
        def builder = Value.newBuilder()
        Type.get(value.class).setValue(builder, value)
        builder.build()
    }

    DimensionElement buildDimensionElement(Dimension dim, @Nonnull Object value) {
        def builder = DimensionElement.newBuilder()
        if (dim.elementsSerializable) {
            def serializedValue = dim.asSerializable(value)
            Type.get(serializedValue.class).setValue(builder, serializedValue)
        } else {
            List<Property> elementProperties = dim.elementFields.values().asList()
            for (int i=0; i<elementProperties.size(); i++) {
                def fieldVal = elementProperties[i].get(value)
                if(fieldVal == null) {
                    // 1-based!
                    builder.addAbsentFieldIndices(i+1)
                } else {
                    builder.addFields(buildValue(fieldVal))
                }
            }
        }
        return builder.build()
    }

    protected DimensionElements.Builder buildDimensionElements(Dimension dim, List dimElements, boolean setName = false) {
        def builder = DimensionElements.newBuilder()

        if(setName) builder.name = dim.name

        boolean empty = true
        boolean elementsPresent = false

        for(int i=0; i<dimElements.size(); i++) {
            if(dimElements[i] == null) {
                builder.addAbsentElementIndices(i+1)
            } else {
                elementsPresent = true
            }
        }

        if(elementsPresent) {
            def properties = (dim.elementsSerializable
                    ? ImmutableList.of(new IdentityProperty(null, dim.elementType))
                    : dim.elementFields.values().asList())

            for(int i=0; i<properties.size(); i++) {
                def fieldColumn = buildElementFields(properties[i], dimElements)
                if(fieldColumn == null) {
                    builder.addAbsentFieldColumnIndices(i+1)
                } else {
                    builder.addFields(fieldColumn)
                    empty = false
                }
            }
        }

        if(empty) {
            builder.empty = true
            builder.clearAbsentElementIndices()
            builder.clearAbsentFieldColumnIndices()
            builder.clearFields()
        }

        return builder
    }


    protected DimensionElementFieldColumn buildElementFields(Property prop, List dimElements) {
        DimensionElementFieldColumn.Builder builder = DimensionElementFieldColumn.newBuilder()

        Type type = Type.get(prop.type)

        boolean allEmpty = true
        for(int i=0; i<dimElements.size(); i++) {
            def element = dimElements[i]
            if(element == null) continue
            def field = prop.get(element)
            if(field == null) {
                builder.addAbsentValueIndices(i+1)
            } else {
                allEmpty = false
                type.addToColumn(builder, field)
            }
        }
        return allEmpty ? null : builder.build()
    }

    @Lazy PackedCellBuilder packedCellBuilder = new PackedCellBuilder()
    protected PackedCell createPackedCell() {
        packedCellBuilder.createPackedCell()
    }

    class PackedCellBuilder {
        private PackedCell.Builder builder = PackedCell.newBuilder()
        private Class valueType

        private boolean sameIndices(HypercubeValue val, ArrayList<Integer> indices) {
            for (int i = 0; i < indices.size(); i++) {
                if (val.getDimElementIndex(indexedDims[i]) != indices[i]) return false
            }
            return true
        }

        private ArrayList<Integer> indices(HypercubeValue val) {
            def groupIndices = new ArrayList(indexedDims.size())
            for (Dimension d in indexedDims) {
                groupIndices.add(val.getDimElementIndex(d))
            }
            groupIndices
        }

        private Pair<ArrayList<HypercubeValue>, Class> nextGroup() {
            ArrayList<HypercubeValue> group = []
            HypercubeValue prototype = iterator.next()
            def groupValueType = new Reference<Class>(prototype.value?.class)
            def groupIndices = indices(prototype)
            group.add(prototype)

            while (iterator.hasNext() && sameIndices(iterator.peek(), groupIndices)) {
                Class valueType = iterator.peek().value?.class
                if (!compatibleValueType(valueType, groupValueType)) {
                    log.warn("Observations with incompatible value types found for the same concept or projection. " +
                            "Got $valueType.simpleName while previous observation(s) had values of type ${this.valueType.simpleName}")
                    break
                }
                group.add(iterator.next())
            }

            new Pair(group, groupValueType.get())
        }

        boolean compatibleValueType(Class type, Reference<Class> groupValueType) {
            // The prototype may not have a value at all, and therefore a null value type. So we need to handle three cases:
            // null, String, or Number.
            if (groupValueType.get() == null) {
                groupValueType.set type
                return true
            } else {
                return groupValueType.get().is(type)
            }
        }

        protected PackedCell createPackedCell() {
            builder.clear()
            valueType = null

            Pair<ArrayList<HypercubeValue>, Class> groupAndType = nextGroup()
            ArrayList<HypercubeValue> group = groupAndType.aValue
            valueType = groupAndType.bValue
            HypercubeValue prototype = group[0]

            putIndexedDims(prototype)

            // shuffle the group list so that values that do not have an element for the packed dimension are at the
            // front. putValues expects that
            moveNullPackedDimensionToFront(group)

            // The values are grouped by their packed dimension element, but still in one list. To ease further
            // processing transform this into a list of lists, one for each
            List<List<HypercubeValue>> groupedSamples = groupSamples(group)

            putValues(groupedSamples)

            putInlineDims(groupedSamples)

            if(!iterator.hasNext()) builder.last = true

            builder.build()
        }

        private List<List<HypercubeValue>> groupSamples(List<HypercubeValue> values) {
            List<List<HypercubeValue>> groupedValues = []

            // We assume that the values are already grouped by their packedDimension, and that values that do not
            // have an element for the packed dimension are also grouped together. The rest of this code assumes that
            // these elements are the first group.

            Integer currentPackIndex = null
            int startIdx = 0
            for(int i=0; i<values.size(); i++) {
                def hvPackIndex = values[i].getDimElementIndex(packedDimension)
                if(hvPackIndex != currentPackIndex) {
                    groupedValues << values.subList(startIdx, i)
                    startIdx = i
                    currentPackIndex = hvPackIndex
                }
            }
            groupedValues << values.subList(startIdx, values.size())

            return groupedValues
        }

        /**
         * Moves the HypercubeValues that do not have an element for the packed dimension to the fron of the list, and
         * returns how many of them were found
         */
        private int moveNullPackedDimensionToFront(List<HypercubeValue> group) {
            int firstNonNull = 0
            for(int i=0; i<group.size(); i++) {
                if (group[i].getDimElementIndex(packedDimension) == null) {
                    group.swap(i, firstNonNull)
                    firstNonNull++
                }
            }
        }

        private void putIndexedDims(HypercubeValue prototype) {
            // put dimension indexes
            for (Dimension d in indexedDims) {
                Integer idx = prototype.getDimElementIndex(d)
                // convert to 1-based values, 0 means not present
                builder.addDimensionIndexes(idx == null ? 0 : idx + 1)
            }
        }

        private void putValues(List<List<HypercubeValue>> groups) {

            // put values which do not have an element for the packed dimension. These must be in groups[0]
            for(hv in groups[0]) {
                addValue(hv)
            }
            builder.setNullElementCount(groups[0].size())

            // group[0] has been processed
            groups = groups.subList(1, groups.size())

            // The list of values corresponds to the list of dimension elements. In the basic case there is a one-to-one
            // correspondence. So if we are packing along the Patient dimension, we will have a list of e.g. 50 patients
            // with indexes 0..49. If there is one observation per patient (for each concept, trial visit, and other
            // indexed dimension) the `group` list will contain a list of 50 HypercubeValue's, one for each patient for
            // the current concept-trialVisit-etc combination. In that case creating a PackedCell would be as simple as
            // taking all the 50 values an putting them in the builder. However the correspondence might not be
            // one-to-one. Observations for some patients might be missing, there could be multiple observations for a
            // patient, or there could be observations that do not belong to any patient. (The latter is unlikely for the
            // specific example of packing along the patient dimension, but in general the data model supports such cases.)
            //
            // The PackedCell supports two modes of handling these non-one-to-one cases. One uses the absentValues field
            // to indicate for which patients there is no value. This mode assumes there is at most one observation per
            // patient. The second mode uses the sampleCounts field, which for each patient indicates the number of
            // values (which might be zero). In either case the values that do not have a patient are prepended to the
            // list of values, and the nullElementCount field indicates how many such values there are. (This is expected
            // to be zero most of the time.)

            // Detect which mode to use
            boolean sampleCountMode = false
            for(group in groups) {
                if(group.size() > 1) {
                    sampleCountMode = true
                    break
                }
            }

            for (int i=0; i<groups.size(); i++) {
                def group = groups[i]
                if(sampleCountMode) {
                    builder.addSampleCounts(group.size())
                } else if(group.size() == 0) {
                    // 1-based
                    builder.addAbsentValues(i + 1)
                    continue
                }

                for (hv in group) {
                    addValue(hv)
                }
            }
        }

        private void addValue(HypercubeValue value) {
            if (String.is(valueType)) {
                builder.addStringValues(value.value.toString())
            } else {
                builder.addNumericValues(((Number) value.value).toDouble())
            }
        }

        private void putInlineDims(List<List<HypercubeValue>> groups) {
            for(dim in inlineDims) {
                builder.addInlineDimensions(inlineDimension(groups, dim))
            }
        }

        static final byte PERPACK = 0
        static final byte PERPACKELEMENT = 1
        static final byte PEROBSERVATION = 2

        private DimensionElements.Builder inlineDimension(List<List<HypercubeValue>> groups, Dimension dim) {
            byte mode = getMode(groups, dim)
            def builder

            if(mode == PERPACK) {
                builder = buildDimensionElements(dim, [firstNestedElement(groups)[dim]])
                builder.setPerPackedCell(true)
            } else if(mode == PERPACKELEMENT) {
                List elements = []
                for(group in groups) {
                    if(!group.empty) elements << group[0][dim]
                }
                builder = buildDimensionElements(dim, elements)
            } else if(mode == PEROBSERVATION) {
                List elements = []
                for(group in groups) for(hv in group) {
                    elements << hv[dim]
                }
                builder = buildDimensionElements(dim, elements)
                builder.setPerSample(true)
            } else throw new AssertionError((Object) "unreachable")

            builder.setName(dim.name)

            return builder
        }

        private <T> T firstNestedElement(List<List<T>> lists) {
            for(list in lists) { for(elem in list) { return elem } }
        }

        private byte getMode(List<List<HypercubeValue>> values, Dimension dim) {
            def firstElement = firstNestedElement(values)[dim] // We know there is at least one value in the list of lists, otherwise this wouldn't be called

            boolean allEqual = true
            for(group in values) {
                if(group.empty) continue
                def groupElement = group[0][dim]
                // Note: dynamic call to .equals!
                if(allEqual && firstElement != groupElement) allEqual = false
                if(group.size() == 1) continue

                for(hv in group.subList(1, group.size())) {
                    if(!groupElement.equals(hv[dim])) return PEROBSERVATION
                }
            }

            return allEqual ? PERPACK : PERPACKELEMENT
        }
    }


    protected Footer buildFooter() {
        def builder = Footer.newBuilder()
        for(dim in cube.dimensions.findAll { it.density.isDense }) {
            builder.addDimension(buildDimensionElements(dim, cube.dimensionElements(dim), true))
        }
        builder.build()
    }

    protected Error createError(String error) {
        Error.newBuilder().setError(error).build()
    }

    /**
     * Find the dimension on which this hypercube can be packed. Return null if not packable.
     */
    Dimension findPackableDimension(Collection<Dimension> sortedDims, Collection<Dimension> indexedDims) {
        // The requirement is that the data as retrieved from core-api is grouped in such a way that all values that
        // have the same indexed dimension coordinates are grouped together

        if(!indexedDims.every { it in sortedDims }) return null

        // If there is sorting on a non-indexed dimension, that

        boolean sparseDimensionSeen = false
        Dimension lastIndexedDim = null
        for(dim in sortedDims) {
            if(dim.density.isDense && sparseDimensionSeen) return null
            if(dim.density.isSparse) sparseDimensionSeen = true
            if(dim.density.isDense) lastIndexedDim = dim
        }
        return lastIndexedDim.packable.packable ? lastIndexedDim : null
    }

    void init(Map args, Hypercube cube, OutputStream out) {
        this.cube = cube
        this.out = out

        ImmutableSet<Dimension> sortedDims = cube.sorting.keySet()
        Dimension packDim = findPackableDimension(sortedDims, cube.dimensions.findAll { it.density.isDense })

        if (args.pack && packDim != null) {
            packedDimension = packDim
            packingEnabled = true
        } else {
            packedDimension = null
            packingEnabled = false
        }

        this.inlineDims = cube.dimensions.findAll { it != packedDimension && it.density.isSparse }
        this.indexedDims = cube.dimensions.findAll { it != packedDimension && it.density.isDense }

        this.iterator = cube.iterator()
    }

    void write(Map args, Hypercube cube, OutputStream out) {
        init(args, cube, out)

        try {
            buildHeader().writeDelimitedTo(out)

            if(!packingEnabled) {
                while(iterator.hasNext()) {
                    def message = createCell(iterator.next())
                    message.writeDelimitedTo(out)
                }
            } else {
                while(iterator.hasNext()) {
                    def message = createPackedCell()
                    message.writeDelimitedTo(out)
                }
            }

            buildFooter().writeDelimitedTo(out)

        } catch(Exception e) {

            log.error("Exception while writing protobuf result", e)

            try {
                // The Error message is compatible with Header, Footer, Cell and PackedCell so we can send that instead
                // of any of those. This does assume that an exception is not thrown while a partial message has been
                // written. However the protobuf implementation tries its best to write full messages in one go.
                createError(e.toString()).writeDelimitedTo(out)
            } catch(Exception e2) {
                // If they're both IOException's they are probably due to the same problem. Although theoretically
                // the database i/o or some other i/o might cause the first IOException.
                if(!(e instanceof IOException && e2 instanceof IOException)) {
                    log.error("Unable to write previous error to the protobuf result stream", e2)
                }
            }

            throw e
        }
    }

}

