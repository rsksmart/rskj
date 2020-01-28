package co.rsk.db.importer.provider.index;

import co.rsk.db.importer.BootstrapImportException;
import co.rsk.db.importer.provider.index.data.BootstrapDataEntry;
import co.rsk.db.importer.provider.index.data.BootstrapDataIndex;
import co.rsk.db.importer.provider.index.data.BootstrapDataSignature;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

public class BootstrapIndexCandidateSelectorTest {

    @Test(expected= BootstrapImportException.class)
    public void getMaximumCommonHeightDataEmpty() {
        List<String> keys = Arrays.asList("key1", "key2");
        BootstrapIndexCandidateSelector indexMCH = new BootstrapIndexCandidateSelector(keys, 2);
        List<BootstrapDataIndex> indexes = new ArrayList<>();
        indexMCH.getHeightData(indexes);
    }

    @Test(expected=BootstrapImportException.class)
    public void getMaximumCommonHeightDataOneEntry() {
        List<String> keys = Arrays.asList("key1", "key2");
        BootstrapIndexCandidateSelector indexMCH = new BootstrapIndexCandidateSelector(keys, 2);
        List<BootstrapDataIndex> indexes = new ArrayList<>();
        ArrayList<BootstrapDataEntry> entries = new ArrayList<>();
        entries.add(new BootstrapDataEntry(1, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        indexes.add(new BootstrapDataIndex(entries));
        indexMCH.getHeightData(indexes);
    }

    @Test(expected=BootstrapImportException.class)
    public void getMaximumCommonHeightDataDuplicatedEntries() {
        List<String> keys = Arrays.asList("key1", "key2");
        BootstrapIndexCandidateSelector indexMCH = new BootstrapIndexCandidateSelector(keys, 2);
        List<BootstrapDataIndex> indexes = new ArrayList<>();
        ArrayList<BootstrapDataEntry> entries = new ArrayList<>();
        entries.add(new BootstrapDataEntry(1, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        entries.add(new BootstrapDataEntry(1, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        indexes.add(new BootstrapDataIndex(entries));
        indexMCH.getHeightData(indexes);
    }

    @Test
    public void getMaximumCommonHeightDataTwoEntries() {
        List<String> keys = Arrays.asList("key1", "key2");
        BootstrapIndexCandidateSelector indexMCH = new BootstrapIndexCandidateSelector(keys, 2);
        List<BootstrapDataIndex> indexes = new ArrayList<>();
        ArrayList<BootstrapDataEntry> entries = new ArrayList<>();
        ArrayList<BootstrapDataEntry> entries2 = new ArrayList<>();
        entries.add(new BootstrapDataEntry(1, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        entries2.add(new BootstrapDataEntry(1, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        indexes.add(new BootstrapDataIndex(entries));
        indexes.add(new BootstrapDataIndex(entries2));
        BootstrapIndexCandidateSelector.HeightCandidate heightCandidate = indexMCH.getHeightData(indexes);
        assertEquals(1, heightCandidate.getHeight());
    }

    @Test
    public void getMaximumCommonHeightDataThreeEntries() {
        List<String> keys = Arrays.asList("key1", "key2");
        BootstrapIndexCandidateSelector indexMCH = new BootstrapIndexCandidateSelector(keys, 2);
        List<BootstrapDataIndex> indexes = new ArrayList<>();
        ArrayList<BootstrapDataEntry> entries = new ArrayList<>();
        ArrayList<BootstrapDataEntry> entries2 = new ArrayList<>();
        entries.add(new BootstrapDataEntry(1, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        entries.add(new BootstrapDataEntry(2, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        entries2.add(new BootstrapDataEntry(1, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        indexes.add(new BootstrapDataIndex(entries));
        indexes.add(new BootstrapDataIndex(entries2));
        BootstrapIndexCandidateSelector.HeightCandidate heightCandidate = indexMCH.getHeightData(indexes);
        assertEquals(1, heightCandidate.getHeight());
    }


    @Test
    public void getMaximumCommonHeightDataManyEntries() {
        List<String> keys = Arrays.asList("key1", "key2", "keys3");
        BootstrapIndexCandidateSelector indexMCH = new BootstrapIndexCandidateSelector(keys, 2);
        List<BootstrapDataIndex> indexes = new ArrayList<>();
        ArrayList<BootstrapDataEntry> entries = new ArrayList<>();
        ArrayList<BootstrapDataEntry> entries2 = new ArrayList<>();
        ArrayList<BootstrapDataEntry> entries3 = new ArrayList<>();

        entries.add(new BootstrapDataEntry(1, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        entries2.add(new BootstrapDataEntry(1, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        entries3.add(new BootstrapDataEntry(1, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));

        entries.add(new BootstrapDataEntry(2, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        entries2.add(new BootstrapDataEntry(2, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));
        entries3.add(new BootstrapDataEntry(2, "", "dbPath", "hash", new BootstrapDataSignature("r", "s")));

        indexes.add(new BootstrapDataIndex(entries));
        indexes.add(new BootstrapDataIndex(entries2));
        indexes.add(new BootstrapDataIndex(entries3));

        BootstrapIndexCandidateSelector.HeightCandidate heightCandidate = indexMCH.getHeightData(indexes);
        assertEquals(heightCandidate.getHeight(), 2L);
    }
}