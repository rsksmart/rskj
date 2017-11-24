/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.db;

import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.EventInfo;
import co.rsk.core.bc.EventInfoItem;
import co.rsk.core.bc.Events;
import co.rsk.test.World;
import co.rsk.test.builders.BlockBuilder;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.EventsStore;
import org.ethereum.db.EventsStoreImpl;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by SerAdmin on 11/21/2017.
 */
public class EventsStoreImplTest {

    final byte[] sender = {0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
            0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,0x10,0x11,0x12,0x13,0x14};
    final byte[] receiver = {0x11,0x12,0x13,0x14,0x15,0x16,0x17,0x18,
            0x19,0x1A,0x1B,0x1C,0x1D,0x1E,0x1F,0x20,0x21,0x22,0x23,0x24};

    @Test
    public void encodeAndDecode() {
        // Now we'll check that the EventsLog contains the correct data
        // Let's build it by hand!
        List<EventInfoItem> eventList = createEventList(3);
        byte[] rlp =Events.encodeEventList(eventList);
        ArrayList<EventInfoItem> newList = new ArrayList<>();
        Events.decodeEventList(newList,rlp);
        byte[] rlp2 =Events.encodeEventList(newList);
        Assert.assertArrayEquals(rlp,rlp2);

    }

    public EventInfoItem createEvent(int i) {
        List<DataWord> topics = new ArrayList<>();
        topics.add(new DataWord(sender));
        topics.add(new DataWord(0x12+i)); // sample topic
        byte[] data = new byte[0x28];
        new DataWord(3).copyLastNBytes(data, 0, 8);
        new DataWord(10000+i).copyTo(data, 8);

        EventInfo eventInfo = new EventInfo(topics, data, i);
        return new EventInfoItem(eventInfo,receiver);
    }

    public List<EventInfoItem> createEventList(int n) {
        List<EventInfoItem> eventList= new ArrayList<>();
        for (int i=0;i<n;i++)
            eventList.add(createEvent(i));
        return eventList;
    }

    @Test
    public void getUnknownKey() {
        EventsStore store = new EventsStoreImpl(new HashMapDB());
        byte[] key = new byte[] { 0x01, 0x02 };

        List<EventInfoItem> events = store.get(key);

        Assert.assertNull(events);
    }

    public List<EventInfoItem> createEvents() {
        List<EventInfoItem> events = new ArrayList<>();
        events.addAll(createEventList(5));
        return events;
    }

    @Test
    public void addAndGetEvent() {
        EventsStore store = new EventsStoreImpl(new HashMapDB());

        List<EventInfoItem> events = createEvents();
        byte[] blockHash = Hex.decode("0102030405060708");

        store.save(blockHash,  events);
        List<EventInfoItem> result = store.get(blockHash);

        Assert.assertEquals(events.size(),result.size());
        for (int i =0; i< events.size();i++) {
            EventInfoItem e1 = events.get(i);
            EventInfoItem e2 = result.get(i);
            Assert.assertEquals(e1,e2);
        }
        Assert.assertArrayEquals(Events.encodeEventList(events), Events.encodeEventList(result));
    }

}
