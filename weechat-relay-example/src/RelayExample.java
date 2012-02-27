import com.ubergeek42.weechat.ChatBufferObserver;
import com.ubergeek42.weechat.ChatBuffers;
import com.ubergeek42.weechat.WeechatBuffer;
import com.ubergeek42.weechat.weechatrelay.WRelayConnection;

public class RelayExample implements ChatBufferObserver {
	static ChatBuffers cb = new ChatBuffers();
	public static void main(String[] args) {
		String server = "10.0.0.1";
		String port = "8001";
		String password = "testpassword";
		
		WRelayConnection wr = new WRelayConnection(server, port, password);
		wr.connect();
		
		// Hook a handler for testing the infolist functionality
		wr.addHandler("infolist-test", new InfolistMessageHandler());
		wr.sendMsg("infolist-test","infolist", "buffer");
		
		// Hook a handler for testing the "info" functionality
		wr.addHandler("info-test", new InfoMessageHandler());
		wr.sendMsg("info-test", "info", "version");
		
		// Hook a handler for testing hdata functionality
		cb.onChanged(new RelayExample());
		wr.addHandler("listbuffers", cb);
		wr.sendMsg("listbuffers","hdata","buffer:gui_buffers(*) number,full_name,short_name,type,title,nicklist,local_variables");
		// Please view the source for ChatBuffers to see how this was handled
		// ChatBuffers also handles a bunch of other special event messages(such as _buffer_opened, or _buffer_closed)
		
		
		// Sleep a bit to get our messages, then quit
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		wr.disconnect();
	}

	@Override
	public void onBuffersChanged() {
		System.out.println("[Buffer list]");
		for (int i=0;i<cb.getNumBuffers(); i++) {
			WeechatBuffer wb = cb.getBuffer(i);
			// Just print some simple information about the buffer
			System.out.println("  " + wb.getShortName() + " " + wb.getFullName());
		}
	}
}
