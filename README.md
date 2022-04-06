# SDN_forwading_uscrl

~~~
cd cobaal-app
mvn clean install
onos-app localhost install! target/cobaal-app-1.0-SNAPSHOT.oar
~~~

Ehternet port 1 of an OVS should be connected its a host device.
ARP table of all hosts should be provided in advance (there is no need to be precise).
If device id of the OVS is "of:000...00x", IP of host connected the OVS should be set "10.0.0.x".
