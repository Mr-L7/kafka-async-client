package kafka.async.ops;

import java.nio.ByteBuffer;

import kafka.async.KafkaAsyncProcessor;
import kafka.async.KafkaBrokerIdentity;
import kafka.async.KafkaOperation;
import kafka.async.client.MessageSet;
import kafka.async.futures.ValueFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FetchRequest implements KafkaOperation {

	static Logger logger = LoggerFactory.getLogger(FetchRequest.class);

	final KafkaBrokerIdentity broker;
	final byte[] topicName;
	final int partition;
	final long offset;
	final int maxSize;

	final ValueFuture<MessageSet> result;
	
	public FetchRequest(KafkaBrokerIdentity broker, byte[] topicName, int partition, long offset, int maxSize) {
		this.broker = broker;
		this.topicName = topicName;
		this.partition = partition;
		this.offset = offset;
		this.maxSize = maxSize;
		
		this.result = new ValueFuture<MessageSet>();
	}
	
	public ValueFuture<MessageSet> getResult() {
		return result;
	}
	
	@Override
	public boolean start() {
		return result.beginExecution();
	}
	
	@Override
	public boolean canRead() {
		return true;
	}
	
	@Override
	public boolean executeRead(ByteBuffer buffer) {
		int size = buffer.getInt(0) + KafkaAsyncProcessor.SIZEOF_INT32;
		if (buffer.position() < size) {
			return false;
		}
		buffer.flip();

		ByteBuffer slice = buffer.slice();
		slice.limit(size);
		
		ByteBuffer contents = ByteBuffer
				.allocate(size)
				.put(slice)
				.asReadOnlyBuffer();
		contents.flip();

		// Skip everything
		buffer.position(size);
		
		if (logger.isTraceEnabled()) {
			logger.trace("Processing fetch response ("+size+" bytes)");
		}
		
		contents.getInt();
		int errorCode = contents.getShort();
		if (logger.isTraceEnabled()) {
			logger.trace("Response status was code: "+errorCode);
		}
		if (errorCode != 0) {
			throw new RuntimeException("Kafka reported error code "+errorCode);
		}

		MessageSet messages = new MessageSet(this.offset,contents);
		
		result.completeWithValue(messages);
		return true;
	}
	
	@Override
	public void executeWrite(ByteBuffer buffer) {
		int size = 0;
		
		short requestType = 1; // 1=FETCH
		
		// Request length (int32) placeholder
		int requestSizePosition = buffer.position();
		buffer.putInt(0);
		
		// Request type (int16)
		buffer.putShort(requestType);
		
		// Topic length (int16)
		buffer.putShort((short)topicName.length);
		
		// Topic (byte[])
		buffer.put(topicName);
		
		// Partition (int32)
		buffer.putInt(partition);
		
		// Requested offset (int64)
		buffer.putLong(offset);
		
		// Max size (int32)
		buffer.putInt(maxSize);
		
		size = buffer.position() - requestSizePosition - KafkaAsyncProcessor.SIZEOF_INT32;
		buffer.putInt(requestSizePosition,size);
	}
	
	@Override
	public void writeComplete() {
	}
	
	@Override
	public void responseFailed(Exception reason) {
		result.completeWithException(reason);
	}
	
	@Override
	public void requestFailed(Exception reason) {
		result.completeWithException(reason);
	}
	
	@Override
	public void brokerFailed(Exception reason) {
		result.completeWithException(reason);
	}

	@Override
	public KafkaBrokerIdentity getTargetBroker() {
		return broker;
	}
	
}