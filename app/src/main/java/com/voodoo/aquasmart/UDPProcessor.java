package com.voodoo.aquasmart;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Класс UDP коммуникатора
 * @author Ostapenko
 *
 */
public final class UDPProcessor
{
	/**
	 * Размер входного буфера
	 */
	private static final int INPUT_BUFFER_SIZE = 2048;
	/**
	 * Таймаут вычитки входного пакета
	 */
	private static final int RECEIVE_TIMEOUT = 500;

	private OnConnectionListener mConnListener;
	private OnReceiveListener mReceiveListener;
	private boolean mIsActive = false;

	private int mPort;
	private InetAddress mSelfIP;
	private InetAddress mBroadcastIP;

	private boolean mReceiverRunned = false;
	private Thread mReceiverThread;
	private DatagramSocket mReceiverSocket;
	private Handler mEventTranslator;



	/**
	 * Конструктор
	 * @param port UDP порт
	 */
	public UDPProcessor(int port)
	{
		this.mPort = port;
		mEventTranslator = new Handler();
	}

	/**
	 * Получить состояние активности процесса коммуникации
	 * @return
	 */
	public final boolean getIsActive()
	{
		return mIsActive;
	}

	/**
	 * Передать широковещательный пакет
	 * @param frame Пакет
	 */
	public boolean send(@NonNull final IDataFrame frame)
	{
		return send(mBroadcastIP, frame);
	}

	/**
	 * Передать адресный пакет
	 * @param destination Адрес приемника пакета
	 * @param frame Пакет
	 * @return
	 */
	public boolean send(@NonNull final InetAddress destination, @NonNull final IDataFrame frame)
	{
		final int BROADCAST_IP_BYTE_INDEX = 0;

		if (!mIsActive)
			return false;

		try
		{
			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					DatagramSocket senderSocket = null;
					try
					{
						senderSocket = new DatagramSocket();
						if (destination.getAddress()[BROADCAST_IP_BYTE_INDEX] == 255)
							senderSocket.setBroadcast(true);

						DatagramPacket packet = new DatagramPacket(frame.getFrameData(), frame.getFrameLength(), destination, mPort);
						senderSocket.send(packet);
					}
					catch (IOException e)
					{ e.printStackTrace(); }
					finally
					{
						if (senderSocket != null)
							senderSocket.close();
					}
				}
			}).start();
		}
		catch (Exception e)
		{
			Log.e(this.getClass().getName(), e.getMessage());
			return false;
		}

		return true;
	}

	/**
	 * Запустить процесс коммуникации
	 * @return
	 */
	public final boolean start()
	{
		if (mIsActive)
			return false;

		byte[] ipBytes = IPHelper.getLocalIP4AsBytes();
		byte[] bipBytes = IPHelper.getBroadcastIP4AsBytes();
		if ((ipBytes == null) || (bipBytes == null))
			return false;

		try
		{
			mSelfIP = InetAddress.getByAddress(ipBytes);
			mBroadcastIP = InetAddress.getByAddress(bipBytes);
		}
		catch (UnknownHostException e)
		{
			Log.e(this.getClass().getName(), e.getMessage());
			return false;
		}

		try
		{
			mReceiverSocket = new DatagramSocket(mPort);
			mReceiverSocket.setSoTimeout(RECEIVE_TIMEOUT);
		}
		catch(SocketException e)
		{
			Log.e(this.getClass().getName(), e.getMessage());
			return false;
		}


		mReceiverThread = new Thread(receiver);
		mReceiverThread.start();
		mIsActive = true;

		return true;
	}

	/**
	 * Прервать процесс коммуникации
	 */
	public final void stop()
	{
		if (mIsActive)
		{
			mReceiverRunned = false;
			try
			{ mReceiverThread.join(1000); }
			catch(Exception e) { }

			mIsActive = false;
			mReceiverSocket.close();

			raiseOnDisconnected(true);
		}
	}


	/**
	 * Информировать слушателя о разрыве связи
	 * @param byUser Признак, определяющий был ли разрыв связи иницирован пользователем
	 */
	private void raiseOnDisconnected(final boolean byUser)
	{
		if (mConnListener != null)
			mEventTranslator.post(new Runnable()
			{
				@Override
				public void run()
				{ mConnListener.onDisconnected(byUser); }
			});
	}

	/**
	 * Информировать слушателя о получении пакета
	 * @param ip Адрес отправителя пакета
	 * @param frame Пакет
	 */
	private void raiseOnFrameReceived(final InetAddress ip, final IDataFrame frame)
	{
		if (mReceiveListener != null)
			mEventTranslator.post(new Runnable()
			{
				@Override
				public void run()
				{ mReceiveListener.onFrameReceived(ip, frame); }
			});
	}

	/**
	 * Установить слушателя для события изменения состояния подключения
	 * @param listener
	 */
	public void setOnConnectionListener(@Nullable OnConnectionListener listener)
	{
		mConnListener = listener;
	}

	/**
	 * Установить слушателя для события получения пакета
	 * @param listener Слушатель
	 */
	public void setOnReceiveListener(@Nullable OnReceiveListener listener)
	{
		mReceiveListener = listener;
	}

	private Runnable receiver = new Runnable()
	{
		@Override
		public void run()
		{
			mReceiverRunned = true;

			byte[] packetBuffer = new byte[INPUT_BUFFER_SIZE];
			while (mReceiverRunned)
			{
				try
				{
					DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length);
					mReceiverSocket.receive(packet);
					if (packet.getLength() > 0)
					{
						if (!packet.getAddress().equals(mSelfIP))
						{
							DataFrame inputFrame = new DataFrame(Arrays.copyOf(packet.getData(), packet.getLength()));
							//if (inputFrame.isCorrect())
								raiseOnFrameReceived(packet.getAddress(), inputFrame);
						}
					}
				}
				catch (IOException e)
				{
					if (mReceiverSocket.isClosed())
					{
						mReceiverRunned = false;
						mIsActive = false;
						raiseOnDisconnected(false);

						return;
					}
				}
			}
		}
	};


	public interface OnConnectionListener
	{
		/**
		 * Разрыв связи
		 * @param byUser Признак, определяющий был ли разрыв связи иницирован пользователем
		 */
		public void onDisconnected(boolean byUser);
	}

	public interface OnReceiveListener
	{
		/**
		 * Получен пакет
		 * @param ip Адрес отправителя пакета
		 * @param frame Пакет
		 */
		public void onFrameReceived(InetAddress ip, IDataFrame frame);
	}
}