package drfoliberg.master;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import drfoliberg.common.Node;
import drfoliberg.common.Status;
import drfoliberg.common.network.ClusterProtocol;
import drfoliberg.common.network.ConnectMessage;
import drfoliberg.common.network.Message;
import drfoliberg.common.network.StatusReport;

public class HandleMaster implements Runnable {

	ObjectInputStream in;
	ObjectOutputStream out;
	Socket s;
	Master master;

	public HandleMaster(Socket s, Master master) {
		this.master = master;
		this.s = s;
	}
	
	private InetAddress getAddressFromSocket(Socket s) {
		InetAddress ad = ((InetSocketAddress) s.getRemoteSocketAddress())
				.getAddress();
		return ad;
	}

	public void run() {
		boolean close = false;
		try {
			out = new ObjectOutputStream(s.getOutputStream());
			out.flush();
			in = new ObjectInputStream(s.getInputStream());
			Node sender = null;
			while (!close) {
				Object request = in.readObject();
				if (request instanceof Message) {
					switch (((Message) request).getCode()) {

					case CONNECT_ME:
						if(!(request instanceof ConnectMessage)) {
							//TODO handle error
							break;
						}
						ConnectMessage cm = (ConnectMessage) request;
						if (cm.status == Status.FREE) {
							// add node to list
							sender = new Node(getAddressFromSocket(s), cm.localPort, cm.name);
							sender.setUnid(cm.getUnid());
							boolean added = master.addNode(sender);
							if (added) {
								// send UNID to the node
								cm.setUnid(sender.getUnid());
								System.out.println("MASTER HANDLE: Sending unid to node");
								out.writeObject(cm);
								out.flush();
							} else {
								out.writeObject(new Message(ClusterProtocol.BYE));
								out.flush();
								close = true;
								s.close();
							}
						} else if (cm.status == Status.NOT_CONNECTED) {
							// the node want to disconnect
							this.master.nodeShutdown(cm.getUnid());
							out.writeObject(new Message(ClusterProtocol.BYE));
							out.flush();
							close = true;
							s.close();
						}
						break;

//					case TASK_REPORT:
//						if (request instanceof TaskReport) {
//							System.out.println("MASTER HANDLE: received a task report from worker.");
//							TaskReport report = (TaskReport) request;
//							this.master.readTaskReport(report);
//						} else {
//							System.out.println("MASTER HANDLE: received an INVALID task report from worker !");
//						}
//						out.writeObject(new Message(ClusterProtocol.BYE));
//						close = true;
//						s.close();
//						break;
					case STATUS_REPORT:
						if (request instanceof StatusReport) {
							StatusReport report = (StatusReport) request;
							if (report.getTaskReport() != null) {
								master.readTaskReport(report.getTaskReport());
							}
							master.readStatusReport(report);
						}
						out.writeObject(new Message(ClusterProtocol.BYE));
						out.flush();
						close = true;
						s.close();
						break;
					case BYE:
						close = true;
						s.close();
						break;
					default:
						out.writeObject(new Message(ClusterProtocol.BAD_REQUEST));
						s.close();
						close = true;
						break;
					}
				} else {
					out.writeObject(new Message(ClusterProtocol.BAD_REQUEST));
					s.close();
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
}