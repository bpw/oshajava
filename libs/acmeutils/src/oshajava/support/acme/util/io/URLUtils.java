/******************************************************************************

Copyright (c) 2010, Cormac Flanagan (University of California, Santa Cruz)
                    and Stephen Freund (Williams College) 

All rights reserved.  Revision 7939 (Wed Aug 11 12:11:58 EDT 2010)

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

    * Neither the names of the University of California, Santa Cruz
      and Williams College nor the names of its contributors may be
      used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

******************************************************************************/

package oshajava.support.acme.util.io;

import java.io.File;
import java.net.URL;

import oshajava.support.acme.util.Assert;


/**
 * Helper methods for processing URLS.
 */
public class URLUtils {

	public static URL getURLFromString(String base, String url) {
		try {
			return new File((url.startsWith(java.io.File.separator) ? "" : (base + java.io.File.separator)) + url).toURI().toURL();
		} catch (Exception e) {
			Assert.fail(e); 
			return null;
		}
	}

	public static URL[] getURLArrayFromString(String base, String urls) {
		if (urls.length() == 0) return new URL[0];
		String strs[] = urls.split(java.io.File.pathSeparator);
		URL u[] = new URL[strs.length];
		for (int i = 0; i < strs.length; i++) {
			u[i] = getURLFromString(base, strs[i]);
		}
		return u;
	}

}
