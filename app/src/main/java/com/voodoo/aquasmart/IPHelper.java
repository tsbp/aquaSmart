package com.voodoo.aquasmart;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * ��������������� ����� ��� �������\������������ IP �������
 * @author Ostapenko
 *
 */
public final class IPHelper
{
	/**
	 * ������������� ������������� ������������� IP � ���������� ���
	 * @param ip
	 * @return
	 */
	public static byte[] convertToBytes(int ip)
	{
		byte[] parts = new byte[4];
		for (int index = 0; index < 4; index++)
			parts[index] = (byte)((ip >> (8 * index)) & 0xFF);
		
		return parts;
	}

	/**
	 * ������������� IP ������ � ��������� �������������
	 * @param address IP ������
	 * @return
	 */
	@SuppressLint("DefaultLocale")
	public static String convertToString(@NonNull InetAddress address)
	{
		if (address instanceof Inet4Address)
		{
			byte[] ipBytes = address.getAddress();
			return String.format("%d.%d.%d.%d", (int)ipBytes[0] & 0xFF, (int)ipBytes[1] & 0xFF, (int)ipBytes[2] & 0xFF, (int)ipBytes[3] & 0xFF);
		}

		return address.toString();
	}
	
	/**
	 * �������� ��������� IP ����� � ���������� ����
	 * @return
	 */
	public static byte[] getLocalIP4AsBytes()
	{
		try
		{
			List<NetworkInterface> nis = Collections.list(NetworkInterface.getNetworkInterfaces());
			for (NetworkInterface ni : nis)
			{
				if (!ni.isLoopback() && !ni.isPointToPoint())
				{
					List<InetAddress> ias = Collections.list(ni.getInetAddresses());
					for (InetAddress ia : ias)
					{
						if ((ia instanceof Inet4Address) && ia.isSiteLocalAddress())
							return ia.getAddress();
					}	
				}
			}
		}
		catch(Exception e)
		{ Log.e(IPHelper.class.getName(), e.getMessage()); }
		
		return null;
	}
	
	/**
	 * �������� ����������������� IP ����� ��������� ������� � ���������� ���� 
	 * @return
	 */
	public static byte[] getBroadcastIP4AsBytes()
	{
		byte[] localParts = getLocalIP4AsBytes();
		if (localParts != null)
		{
			try
			{
				InetAddress localAdd = InetAddress.getByAddress(localParts); 
				NetworkInterface localIntf = NetworkInterface.getByInetAddress(localAdd);
				if (localIntf != null)
				{
					List<InterfaceAddress> ias = localIntf.getInterfaceAddresses();
					for (InterfaceAddress ia : ias)
					{
						if (localAdd.equals(ia.getAddress()))
							return ia.getBroadcast().getAddress();
					}
				}
			}
			catch (IOException e)
			{ Log.e(IPHelper.class.getName(), e.getMessage()); }
		}
		
		return null;
	}
}