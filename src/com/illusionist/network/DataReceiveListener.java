package com.illusionist.network;

import java.util.EventListener;

public interface DataReceiveListener extends EventListener
{
    void onDataReceive(FileDownloader source, long bytesRecv, long bytesTotal);
}