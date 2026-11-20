package com.xick.app.ui.login

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.xick.app.R
import com.xick.app.databinding.FragmentLoginBinding
import com.xick.app.util.AuthManager
import com.xick.app.util.KickOAuth
import kotlinx.coroutines.launch

/**
 * Sign-in the way Xtra does it: Kick's official sign-in page is shown inside the
 * app's WebView. The user signs in there (the app never sees the password). When
 * Kick redirects to our callback address, the WebView intercepts it, the app
 * reads the code, exchanges it for tokens (PKCE) and closes this screen.
 */
class LoginFragment : Fragment() {

    private var binding: FragmentLoginBinding? = null
    private var finished = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentLoginBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val webView = binding?.webView ?: return
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!KickOAuth.isRedirect(url)) return false
                // Our callback: take the code here instead of loading the bridge page.
                if (!finished) finishWith(request.url)
                return true
            }
        }
        webView.loadUrl(KickOAuth.buildAuthorizeUrl())
    }

    private fun finishWith(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = KickOAuth.handleCallback(uri)
            if (ok && !finished && binding != null) {
                finished = true
                AuthManager.markSignedIn()
                Toast.makeText(requireContext(), R.string.signed_in, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } else if (!ok) {
                Toast.makeText(requireContext(), "Giriş tamamlanamadı, tekrar deneyin", Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
