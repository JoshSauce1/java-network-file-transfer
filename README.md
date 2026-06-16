# Network File Transfer System

A Java client-server file transfer system built to demonstrate core networking concepts including TCP, UDP, sockets, client-server communication, caching, acknowledgments, and timeouts.

This project was created for a computer networking course and includes two transfer approaches:

* **TCP file transfer** using Java sockets for reliable communication
* **UDP Stop-and-Wait file transfer** using datagram packets, acknowledgments, timeouts, file-length messages, and an end-of-transfer marker

## Overview

The system allows a client to upload and download files from a server. It also includes a cache layer that checks whether a requested file is already stored locally before requesting it from the main server.

This helped reinforce how real networked systems move data between machines, how reliability works differently in TCP and UDP, and how intermediary systems like caches can reduce repeated requests to a main server.

## Features

* Client-server file upload and download
* TCP-based reliable file transfer
* UDP Stop-and-Wait file transfer
* ACK-based packet confirmation
* Timeout handling for missing acknowledgments
* File length messaging using `LEN`
* End-of-transfer marker using `END`
* Basic cache server for file retrieval
* Local storage folders for client, server, and cache files

## Technologies Used

* Java
* TCP/IP
* UDP
* Sockets
* DatagramSocket
* ServerSocket
* File I/O
* Client-server architecture

## Project Files

```txt
client.java          # Command-line client for uploading/downloading files
server.java          # Main file server
cache.java           # Cache layer between client and server
tcp_transport.java   # TCP file transfer logic
snw_transport.java   # UDP Stop-and-Wait transfer logic
test1.txt            # Sample test file
```

## How It Works

### TCP Mode

In TCP mode, the client connects to the server using Java sockets. TCP handles reliable delivery, ordering, and connection management automatically.

Basic flow:

```txt
Client -> Server: put file.txt
Client -> Server: file bytes
Server saves file in server_fl
```

For downloads, the client checks the cache first. If the file is not found in the cache, the request is sent to the main server.

```txt
Client -> Cache: get file.txt

If cached:
Cache -> Client: file contents

If not cached:
Cache -> Server: get file.txt
Server -> Cache: file contents
Cache stores file
Client can retrieve file
```

### UDP Stop-and-Wait Mode

In UDP/SNW mode, reliability is handled manually. UDP does not guarantee delivery by itself, so this project adds a simple reliability layer.

Basic flow:

```txt
Sender sends packet
Receiver sends ACK
Sender sends next packet
Receiver sends ACK
Repeat until END message
```

The UDP version uses:

* `LEN:<fileSize>` to send the file size before transfer
* `ACK` messages to confirm received packets
* `TIMEOUT` handling when an ACK is not received
* `END` to mark the completion of a file transfer

## How to Run

Compile the Java files:

```bash
javac *.java
```

Start the server in one terminal:

```bash
java server 5000 tcp
```

Start the cache in another terminal:

```bash
java cache 6000 127.0.0.1 5000 tcp
```

Start the client in a third terminal:

```bash
java client 127.0.0.1 5000 127.0.0.1 6000 tcp
```

To run using the UDP Stop-and-Wait version, replace `tcp` with `snw`:

```bash
java server 5000 snw
java cache 6000 127.0.0.1 5000 snw
java client 127.0.0.1 5000 127.0.0.1 6000 snw
```

## Client Commands

Once the client is running, use:

```txt
put filename.txt
get filename.txt
quit
```

Example:

```txt
put test1.txt
get test1.txt
quit
```

## What I Learned

Through this project, I gained hands-on experience with:

* How clients and servers communicate over a network
* How TCP provides reliable file transfer
* How UDP differs from TCP
* How acknowledgments and timeouts can be used to add reliability to UDP
* How ports and sockets are used for network communication
* How files can be read, transmitted, received, and saved
* How a cache layer can reduce repeated requests to a main server
* How to troubleshoot basic network communication issues

## Future Improvements

Potential improvements include:

* Adding a cleaner command-line interface
* Improving error handling for missing files and failed connections
* Supporting larger binary files more reliably
* Adding packet sequence numbers for stronger UDP reliability
* Organizing source files into a dedicated `src` folder
* Adding more detailed setup instructions and diagrams
