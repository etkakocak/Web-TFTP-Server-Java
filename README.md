# Java Web & TFTP Server with Automated Testing

### Overview
This repository contains implementations of two network servers:    
- A Java ``Web Server`` that serves static files and handles basic HTTP requests.
- A Java ``TFTP Server`` that manages file transfers using the Trivial File Transfer Protocol (TFTP).

Both servers are tested using **pytest** to ensure functionality and reliability. The testing scripts validate server responses, file handling, and protocol adherence under various conditions.

### Academic Reports:
📄 **[Java Web Server (PDF)](/reports/Java%20Web%20Server.pdf)**  
📄 **[Java TFTP Server (PDF)](/reports/Java%20TFTP%20Server.pdf)**


## How to setup the java web server
Open the terminal and navigate to the folder **JavaWebServer**.  
The command to enter next:  
```cmd   
java WebServer 80 public   
```   
Then the server will be on unless the terminal is closed or terminated by ctrl+c, ctrl+d keyboard shortcuts.  
Websites can now be accessed with the http://localhost:80 link.  Different files in the **/public** folder also can be accessed by typing **/directory_name** at the end of the link.  

## How to setup the java TFTP server
Run the Java code **TFTPServer.java**. The server will be on for requests after that. You should send requests depending on your operating system.  

**If you use MacOS or Linux:**  
Read request:
```cmd   
tftp
tftp> connect localhost 69
tftp> mode octet
tftp> get f50b.bin
tftp> quit
```   
Write Request:
```cmd   
tftp
tftp> connect localhost 69
tftp> mode octet
tftp> put C:\Users\etkae\desktop\test.bin
tftp> quit
```   

**If you use Windows:**  
*Make sure that you have installed TFTP Server in Windows because it is usually not pre-installed on Windows.*  
Read request:
```cmd   
tftp -i localhost GET f50b.bin C:\Users\etkae\desktop\test.bin
```   
Write Request:
```cmd   
tftp -i localhost PUT C:\Users\etkae\desktop\test.bin f50b.bin
```   

## How to test the servers
Start by downloading dependencies by:  
```cmd   
pip3 install -r requirements.txt
```  
Test files were edited (port and host) for the application, so you can run it directly. While the server is running, run the following command in a new terminal:  
```cmd   
python -m pytest
```   