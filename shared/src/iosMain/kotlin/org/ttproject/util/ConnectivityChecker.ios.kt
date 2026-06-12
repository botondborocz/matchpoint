package org.ttproject.util

import platform.SystemConfiguration.SCNetworkReachabilityCreateWithAddress
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionRequired
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.sockaddr_in
import platform.posix.AF_INET
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.ExperimentalForeignApi
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import kotlinx.cinterop.reinterpret

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
