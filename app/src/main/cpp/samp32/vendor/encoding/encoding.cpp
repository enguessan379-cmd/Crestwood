#include "encoding.h"
#include <cstring>

// [Crestwood] Serveur francais : le texte SA-MP est en Windows-1252 (Latin-1 : e accent aigu = 0xE9),
// et non en Windows-1251 (cyrillique) comme dans la base d'origine. Les noms de fonctions sont gardes
// (cp2utf / utf2cp / convert_windows1251_to_utf8) pour ne rien changer ailleurs dans le client.

constexpr Encoding::Letter Encoding::m_letters[];

// Octets 0x80-0x9F de Windows-1252 -> Unicode (0 = non defini)
static const int kCp1252High[32] = {
	0x20AC, 0,      0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021,
	0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0,      0x017D, 0,
	0,      0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014,
	0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0,      0x017E, 0x0178
};

static int cp1252ToUnicode(unsigned char c)
{
	if (c < 0x80) return c;
	if (c < 0xA0) return kCp1252High[c - 0x80];
	return c;   // 0xA0-0xFF : identique a Latin-1
}

static void appendUtf8(std::string& out, int cp)
{
	if (cp < 0x80) out += (char)cp;
	else if (cp < 0x800) {
		out += (char)(0xC0 | (cp >> 6));
		out += (char)(0x80 | (cp & 0x3F));
	} else {
		out += (char)(0xE0 | (cp >> 12));
		out += (char)(0x80 | ((cp >> 6) & 0x3F));
		out += (char)(0x80 | (cp & 0x3F));
	}
}

static int unicodeToCp1252(int cp)
{
	if (cp < 0x80) return cp;
	if (cp >= 0xA0 && cp <= 0xFF) return cp;
	for (int i = 0; i < 32; ++i) if (kCp1252High[i] == cp && cp != 0) return 0x80 + i;
	return '?';
}

std::string Encoding::cp2utf(const std::string& s)
{
	std::string ns;
	ns.reserve(s.size() * 2);
	for (unsigned char c : s) {
		int cp = cp1252ToUnicode(c);
		if (cp == 0) cp = '?';
		appendUtf8(ns, cp);
	}
	return ns;
}

std::string Encoding::utf2cp(const std::string& s)
{
	std::string out;
	out.reserve(s.size());
	const size_t n = s.size();
	for (size_t i = 0; i < n; ) {
		unsigned char c = (unsigned char)s[i];
		int cp;
		if (c < 0x80) { cp = c; i += 1; }
		else if ((c & 0xE0) == 0xC0 && i + 1 < n) { cp = ((c & 0x1F) << 6) | (s[i + 1] & 0x3F); i += 2; }
		else if ((c & 0xF0) == 0xE0 && i + 2 < n) { cp = ((c & 0x0F) << 12) | ((s[i + 1] & 0x3F) << 6) | (s[i + 2] & 0x3F); i += 3; }
		else if ((c & 0xF8) == 0xF0 && i + 3 < n) { cp = '?'; i += 4; }
		else { cp = '?'; i += 1; }
		out += (char)unicodeToCp1252(cp);
	}
	return out;
}

bool Encoding::convert_utf8_to_windows1251(const char* utf8, char* windows1251, size_t n)
{
	std::string conv = utf2cp(std::string(utf8, strnlen(utf8, n)));
	memcpy(windows1251, conv.c_str(), conv.size() + 1);
	return true;
}

// Ancienne signature (sortie, entree, longueur) : conversion Windows-1252 -> UTF-8.
void Encoding::convert_windows1251_to_utf8(char* out, const char* in, unsigned int len)
{
	std::string src = len ? std::string(in, strnlen(in, len)) : std::string(in);
	std::string conv = cp2utf(src);
	memcpy(out, conv.c_str(), conv.size() + 1);
}
