package org.ttproject.util

import platform.SystemConfiguration.*
import platform.posix.sockaddr_in
import platform.posix.AF_INET
import kotlinx.cinterop.*

class IosConnectivityChecker : ConnectivityChecker {
    @OptIn(ExperimentalForeignApi::class)
    override fun isConnected(): Boolean {
        return memScoped {
            val zeroAddress = alloc<sockaddr_in>()
            zeroAddress.sin_len = sizeOf<sockaddr_in>().toUByte()
            zeroAddress.sin_family = AF_INET.toUByte()

            val reachability = SCNetworkReachabilityCreateWithAddress(null, zeroAddress.ptr.reinterpret()) ?: return false
            val flags = alloc<SCNetworkReachabilityFlagsVar>()
            if (!SCNetworkReachabilityGetFlags(reachability, flags.ptr)) {
                return false
            }
            val isReachable = (flags.value and kSCNetworkReachabilityFlagsReachable) != 0u
            val needsConnection = (flags.value and kSCNetworkReachabilityFlagsConnectionRequired) != 0u
            isReachable && !needsConnection
        }
    }
}
