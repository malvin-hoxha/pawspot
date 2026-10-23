import { BrowserRouter, Link, Routes, Route } from 'react-router';
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { ProtectedRoute } from './features/auth/components/ProtectedRoute';
import { CreatePostPage } from './pages/CreatePostPage';

import { Header } from './components/layout/Header'
import { PostsPage } from './pages/PostsPage';
import { PostDetailsPage } from './pages/PostDetailsPage'

function App() {

  return (
    <BrowserRouter>
      <Header />

      <main className="p-6">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/posts" element={<PostsPage />} />
          <Route path="/posts/:id" element={<PostDetailsPage />} />
          <Route path="*" element={<h1>404 — Page Not Found</h1>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/posts/create" element={<CreatePostPage />} />
          </Route>
        </Routes>
      </main>
    </BrowserRouter>
  )
}

export default App