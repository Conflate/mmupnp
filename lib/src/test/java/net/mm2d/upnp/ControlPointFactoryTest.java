/*
 * Copyright (c) 2018 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import net.mm2d.upnp.ControlPointFactory.Params;
import net.mm2d.upnp.internal.impl.ControlPointImpl;
import net.mm2d.util.NetworkUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;

@SuppressWarnings("NonAsciiCharacters")
@RunWith(JUnit4.class)
public class ControlPointFactoryTest {
    @Test
    public void create_引数無しでコール() throws Exception {
        ControlPointFactory.create();
    }

    @Test
    public void create_インターフェース指定() throws Exception {
        ControlPointFactory.create(new Params()
                .setInterfaces(NetworkUtils.getAvailableInet4Interfaces())
        );
        ControlPointFactory.create(new Params()
                .setInterfaces(Collections.emptyList())
        );
    }

    @Test
    public void create_インターフェース選別() throws Exception {
        ControlPointFactory.create(new Params()
                .setProtocol(Protocol.IP_V4_ONLY)
                .setInterfaces(NetworkUtils.getNetworkInterfaceList())
        );
        ControlPointFactory.create(new Params()
                .setProtocol(Protocol.IP_V6_ONLY)
                .setInterfaces(NetworkUtils.getNetworkInterfaceList())
        );
        ControlPointFactory.create(new Params()
                .setProtocol(Protocol.DUAL_STACK)
                .setInterfaces(NetworkUtils.getNetworkInterfaceList())
        );
    }

    @Test
    public void create_プロトコルスタックのみ指定() throws Exception {
        ControlPointFactory.create(new Params()
                .setProtocol(Protocol.IP_V4_ONLY)
        );
        ControlPointFactory.create(new Params()
                .setProtocol(Protocol.IP_V6_ONLY)
        );
        ControlPointFactory.create(new Params()
                .setProtocol(Protocol.DUAL_STACK)
        );
    }

    @Test
    public void create_Params() throws Exception {
        ControlPointFactory.create(new Params()
                .setProtocol(Protocol.DEFAULT)
                .setInterfaces(NetworkUtils.getNetworkInterfaceList())
                .setNotifySegmentCheckEnabled(true)
        );
    }
}
